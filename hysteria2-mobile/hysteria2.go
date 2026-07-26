// Package hysteria2 is a gomobile-friendly wrapper around the apernet/hysteria
// v2 client. It exposes a tiny surface — SetContext / NewTunnel / Tunnel — that
// gomobile binds into the Java package `hysteria2`, matching the Kotlin bridge
// (Hysteria2CoreBridge) in the InfinityConnect Android app.
//
// The tunnel reads packets straight from the VpnService TUN file descriptor via
// sing-tun (userspace gVisor stack) and forwards TCP/UDP flows through the
// hysteria QUIC client. The client's own UDP socket is protect()ed via the
// Protector callback so its traffic does not loop back into the TUN.
package hysteria2

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/netip"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/apernet/hysteria/core/v2/client"
	tun "github.com/apernet/sing-tun"
	"github.com/sagernet/sing/common/buf"
	"github.com/sagernet/sing/common/logger"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
)

// Protector is implemented by the Java side (VpnService) to keep the client's
// own sockets outside the TUN — VpnService.protect(fd).
type Protector interface {
	Protect(fd int) bool
}

// TunnelCallbackHandler receives lifecycle/status events from the tunnel.
// gomobile binds this to a Java interface of the same name.
type TunnelCallbackHandler interface {
	OnShutdown()
	OnStatus(level int64, message string)
}

var protector atomic.Value // stores Protector

// SetContext wires the platform Protector. It is named SetContext to mirror the
// Xray gomobile surface used by the app; the argument is the VpnService-backed
// Protector, not an arbitrary Android context.
func SetContext(p Protector) {
	if p != nil {
		protector.Store(p)
	}
}

func currentProtector() Protector {
	if v := protector.Load(); v != nil {
		if p, ok := v.(Protector); ok {
			return p
		}
	}
	return nil
}

// Version returns the wrapper/core version string.
func Version() string {
	return "hysteria2-mobile/1 (core v2)"
}

// clientConfig is the JSON contract with Hysteria2ConfigBuilder on the Kotlin
// side. Only the fields the app emits are modelled.
type clientConfig struct {
	Server string `json:"server"`
	Auth   string `json:"auth"`
	TLS    struct {
		SNI      string `json:"sni"`
		Insecure bool   `json:"insecure"`
	} `json:"tls"`
	Obfs *struct {
		Type       string `json:"type"`
		Salamander *struct {
			Password string `json:"password"`
		} `json:"salamander"`
	} `json:"obfs"`
	Bandwidth *struct {
		Up   string `json:"up"`
		Down string `json:"down"`
	} `json:"bandwidth"`
}

// Tunnel is a running Hysteria2 client bound to a TUN fd.
type Tunnel struct {
	hyClient client.Client
	tunIf    tun.Tun
	tunStack tun.Stack
	handler  TunnelCallbackHandler

	router *router
	direct *directDialer

	up   atomic.Int64
	down atomic.Int64

	closeOnce sync.Once
}

// NewTunnel parses configJson, connects the hysteria client and starts the TUN
// stack over tunFd. It blocks until the client handshake completes, then returns
// the running tunnel (packet forwarding continues in background goroutines).
//
// tunCidr — адрес TUN-интерфейса с префиксом (например "10.10.0.1/30"); нужен
// системному стеку sing-tun (в первом префиксе должно быть ≥2 адреса: сервер и
// клиент). Должен согласовываться с VpnService.Builder.addAddress на стороне
// приложения.
func NewTunnel(configJson string, tunFd int, mtu int, tunCidr string, handler TunnelCallbackHandler) (*Tunnel, error) {
	var cfg clientConfig
	if err := json.Unmarshal([]byte(configJson), &cfg); err != nil {
		return nil, fmt.Errorf("parse config: %w", err)
	}
	if cfg.Server == "" {
		return nil, fmt.Errorf("config: server is empty")
	}

	hyConfig, err := buildClientConfig(&cfg)
	if err != nil {
		return nil, err
	}

	if mtu <= 0 {
		mtu = 1500
	}
	if tunCidr == "" {
		tunCidr = "10.10.0.1/30"
	}
	inet4, err := netip.ParsePrefix(tunCidr)
	if err != nil {
		return nil, fmt.Errorf("config: bad tunCidr %q: %w", tunCidr, err)
	}

	status(handler, 1, "connecting to "+cfg.Server)
	hyClient, _, err := client.NewClient(hyConfig)
	if err != nil {
		return nil, fmt.Errorf("hysteria connect: %w", err)
	}
	status(handler, 1, "handshake ok")

	// ВАЖНО — владение TUN fd. sing-tun оборачивает переданный дескриптор в
	// os.NewFile и закрывает его в tunIf.Close(), то есть забирает во владение.
	// А на стороне Java тем же fd владеет ParcelFileDescriptor (VpnService) и
	// закрывает его сам: двойное закрытие ловит fdsan в libc Android 12+ и
	// роняет ВЕСЬ процесс по SIGABRT.
	//
	// Дублируем дескриптор ЗДЕСЬ, а не на стороне Kotlin: ядру нужно отдавать
	// именно оригинальный fd VpnService (на копии libv2ray не получает обратный
	// трафик), поэтому копию для себя делает тот, кто её закрывает.
	dupFd, err := dupFD(tunFd)
	if err != nil {
		_ = hyClient.Close()
		return nil, fmt.Errorf("dup tun fd %d: %w", tunFd, err)
	}

	tunOpts := tun.Options{
		FileDescriptor: dupFd,
		MTU:            uint32(mtu),
		Inet4Address:   []netip.Prefix{inet4},
		Logger:         logger.NOP(),
	}
	tunIf, err := tun.New(tunOpts)
	if err != nil {
		_ = closeFD(dupFd)
		_ = hyClient.Close()
		return nil, fmt.Errorf("open tun: %w", err)
	}

	t := &Tunnel{
		hyClient: hyClient,
		tunIf:    tunIf,
		handler:  handler,
		router:   newRouter(parseRoutingConfig(configJson)),
		direct:   &directDialer{},
	}

	// Этот форк sing-tun собран без gVisor (WithGVisor=false) — используем
	// системный стек, он не требует gvisor.dev и работает поверх TUN fd.
	//
	// Набор опций сверен с эталонным использованием этого же форка в самой
	// hysteria (app/internal/tun/server.go). Отличие одно и намеренное:
	// ForwarderBindInterface там true, а здесь false — на Android привязка
	// форвардера к интерфейсу конфликтует с VpnService.protect (сокеты ядра
	// обязаны идти МИМО TUN, иначе трафик зацикливается).
	stackOpts := tun.StackOptions{
		Context:    context.Background(),
		Tun:        tunIf,
		TunOptions: tunOpts,
		UDPTimeout: int64((5 * time.Minute).Seconds()),
		Handler:    t,
		Logger:     logger.NOP(),
		// InterfaceFinder нужен стеку, чтобы сопоставлять индексы/имена
		// интерфейсов; без него часть путей внутри стека получает -1 и молча
		// отбрасывает пакеты.
		InterfaceFinder: &interfaceFinder{},
	}
	// Внутри stack fd не переоткрываем — обнуляем, чтобы не было double-close.
	stackOpts.TunOptions.FileDescriptor = 0
	tunStack, err := tun.NewStack("system", stackOpts)
	if err != nil {
		_ = tunIf.Close()
		_ = hyClient.Close()
		return nil, fmt.Errorf("create tun stack: %w", err)
	}
	t.tunStack = tunStack

	if err := tunStack.Start(); err != nil {
		// Системный стек биндит TCP-форвардер на ПЕРВЫЙ адрес префикса
		// (inet4ServerAddress). Если этого адреса нет на TUN-интерфейсе, bind
		// падает с "cannot assign requested address" — значит tunCidr на стороне
		// приложения разошёлся с VpnService.Builder.addAddress. Ошибка обычная,
		// возвращаемая: процесс ронять нельзя, Kotlin покажет её пользователю.
		_ = tunStack.Close()
		_ = tunIf.Close()
		_ = hyClient.Close()
		return nil, fmt.Errorf(
			"start tun stack (tun=%s, форвардер биндится на %s): %w",
			inet4.String(), inet4.Addr().String(), err,
		)
	}
	status(handler, 1, "tunnel up")
	return t, nil
}

// NewConnection handles an inbound TCP flow from the TUN, routing it either
// through the hysteria client (proxy) or directly (bypass). Implements sing-tun's
// TCP handler.
func (t *Tunnel) NewConnection(ctx context.Context, conn net.Conn, m M.Metadata) error {
	defer conn.Close()
	reqAddr := m.Destination.String()

	// Решение по IP назначения.
	dec := t.router.routeByIP(hostFromAddr(reqAddr))
	// Если есть доменные правила — досниффим SNI (может изменить решение на direct).
	if dec == routeProxy && t.router.sniffDomains {
		var host string
		conn, host = sniffTLSHost(conn)
		if host != "" && t.router.routeByDomain(host) == routeDirect {
			dec = routeDirect
		}
	}

	rc, err := t.dial(ctx, dec, reqAddr)
	if err != nil {
		// Причину отказа видно только здесь: sing-tun игнорирует возвращённую
		// ошибку и просто рвёт поток, а снаружи это выглядит как «соединение
		// установилось, но данные не идут».
		status(t.handler, 2, "dial "+reqAddr+" failed: "+err.Error())
		return nil
	}
	defer rc.Close()

	// Двунаправленное копирование. Схема — как в эталонной реализации самой
	// hysteria (app/internal/tun/server.go): ждём ПЕРВОЕ завершившееся
	// направление, после чего defer'ы закрывают обе стороны.
	//
	// Ждать оба направления (пробовалось) нельзя: у sing-tun conn — это поток
	// системного стека, io.Copy на нём не разблокируется сам, и соединения
	// зависали бы до таймаута.
	copyErrCh := make(chan error, 2)
	go func() { n, e := io.Copy(rc, conn); t.up.Add(n); copyErrCh <- e }()
	go func() { n, e := io.Copy(conn, rc); t.down.Add(n); copyErrCh <- e }()
	select {
	case <-ctx.Done():
	case <-copyErrCh:
	}
	return nil
}

// dial открывает исходящее TCP-соединение по решению маршрутизации.
func (t *Tunnel) dial(ctx context.Context, dec decision, addr string) (net.Conn, error) {
	if dec == routeDirect {
		return t.direct.dialTCP(ctx, addr)
	}
	return t.hyClient.TCP(addr)
}

// NewPacketConnection handles an inbound UDP flow from the TUN. Routing decision
// is by destination IP (UDP не сниффим). Implements N.UDPConnectionHandler.
func (t *Tunnel) NewPacketConnection(ctx context.Context, conn N.PacketConn, m M.Metadata) error {
	defer conn.Close()
	if t.router.routeByIP(m.Destination.Addr) == routeDirect {
		return t.directPacketConnection(conn)
	}

	hyUDP, err := t.hyClient.UDP()
	if err != nil {
		return nil
	}
	defer hyUDP.Close()

	// TUN -> hysteria
	go func() {
		for {
			buffer := buf.NewSize(65535)
			dest, rErr := conn.ReadPacket(buffer)
			if rErr != nil {
				buffer.Release()
				return
			}
			data := buffer.Bytes()
			if sErr := hyUDP.Send(data, dest.String()); sErr != nil {
				buffer.Release()
				return
			}
			t.up.Add(int64(len(data)))
			buffer.Release()
		}
	}()
	// hysteria -> TUN
	for {
		data, from, rErr := hyUDP.Receive()
		if rErr != nil {
			return nil
		}
		destAddr, pErr := netip.ParseAddrPort(ensurePort(from))
		if pErr != nil {
			continue
		}
		// buf.As, а НЕ buf.With: With создаёт буфер с end=0, то есть пустой —
		// в TUN уходили бы датаграммы нулевой длины, и ответы (весь QUIC-трафик
		// вроде YouTube) не доходили бы до приложений. As выставляет end=len(data).
		if wErr := conn.WritePacket(buf.As(data), M.SocksaddrFromNetIP(destAddr)); wErr != nil {
			return nil
		}
		t.down.Add(int64(len(data)))
	}
}

// directPacketConnection проксирует UDP-поток напрямую (мимо VPN) через
// защищённый сокет. Один сокет на поток; адрес назначения берётся из каждого
// прочитанного пакета (ReadPacket отдаёт dest).
func (t *Tunnel) directPacketConnection(conn N.PacketConn) error {
	uc, err := t.direct.dialUDP()
	if err != nil {
		return nil
	}
	defer uc.Close()

	// TUN -> сеть (direct)
	go func() {
		for {
			buffer := buf.NewSize(65535)
			dest, rErr := conn.ReadPacket(buffer)
			if rErr != nil {
				buffer.Release()
				return
			}
			ap, pErr := netip.ParseAddrPort(ensurePort(dest.String()))
			if pErr != nil {
				buffer.Release()
				continue
			}
			_, _ = uc.WriteToUDPAddrPort(buffer.Bytes(), ap)
			t.up.Add(int64(buffer.Len()))
			buffer.Release()
		}
	}()
	// сеть -> TUN
	readBuf := make([]byte, 65535)
	for {
		n, from, rErr := uc.ReadFromUDPAddrPort(readBuf)
		if rErr != nil {
			return nil
		}
		if wErr := conn.WritePacket(buf.As(readBuf[:n]), M.SocksaddrFromNetIP(from)); wErr != nil {
			return nil
		}
		t.down.Add(int64(n))
	}
}

// NewError implements sing's exceptions.Handler; forwarded as a status event.
func (t *Tunnel) NewError(_ context.Context, err error) {
	if err != nil {
		status(t.handler, 2, "stack error: "+err.Error())
	}
}

// Close stops the tunnel and releases all resources. Idempotent.
//
// Паника в любом из Close() внутри Go убила бы весь процесс приложения (Go не
// разматывает стек через cgo-границу), поэтому каждый шаг изолирован recover'ом.
// Порядок важен: сначала стек (перестаёт читать TUN), затем сам интерфейс —
// закрывается ТОЛЬКО dup-копия дескриптора, оригиналом владеет
// ParcelFileDescriptor на стороне Java.
func (t *Tunnel) Close() error {
	t.closeOnce.Do(func() {
		safeClose(func() error { return t.tunStack.Close() }, t.tunStack != nil)
		safeClose(func() error { return t.tunIf.Close() }, t.tunIf != nil)
		safeClose(func() error { return t.hyClient.Close() }, t.hyClient != nil)
		if t.handler != nil {
			// Java-колбэк — асинхронно, как и status(): вызывать Java со стека
			// cgo-callback при teardown чревато "unexpected return pc".
			h := t.handler
			go func() {
				defer func() { _ = recover() }()
				h.OnShutdown()
			}()
		}
	})
	return nil
}

// safeClose выполняет closer, гася панику и ошибку. cond позволяет не вызывать
// closer на nil-поле, не дублируя проверки в месте вызова.
func safeClose(closer func() error, cond bool) {
	if !cond {
		return
	}
	defer func() { _ = recover() }()
	_ = closer()
}

// UplinkBytes / DownlinkBytes expose cumulative traffic counters.
func (t *Tunnel) UplinkBytes() int64   { return t.up.Load() }
func (t *Tunnel) DownlinkBytes() int64 { return t.down.Load() }

// status доставляет событие в Java-обработчик асинхронно (на отдельной
// горутине), чтобы не вызывать Java со стека cgo-callback внутри
// экспортированных gomobile-функций (иначе при гонке с teardown возможен
// "unexpected return pc for runtime.cgocallback").
func status(h TunnelCallbackHandler, level int64, msg string) {
	if h == nil {
		return
	}
	go func() {
		defer func() { _ = recover() }()
		h.OnStatus(level, msg)
	}()
}

// buildClientConfig maps the JSON contract onto the hysteria core client Config.
func buildClientConfig(cfg *clientConfig) (*client.Config, error) {
	host, portStr, err := net.SplitHostPort(cfg.Server)
	if err != nil {
		return nil, fmt.Errorf("config: bad server %q: %w", cfg.Server, err)
	}
	port, err := strconv.Atoi(portStr)
	if err != nil {
		return nil, fmt.Errorf("config: bad port %q: %w", portStr, err)
	}
	serverAddr := &net.UDPAddr{IP: net.ParseIP(host), Port: port}
	if serverAddr.IP == nil {
		// Resolve hostname (protect not needed: DNS goes out before TUN is up).
		ips, rErr := net.LookupIP(host)
		if rErr != nil || len(ips) == 0 {
			return nil, fmt.Errorf("config: resolve %q: %w", host, rErr)
		}
		serverAddr.IP = ips[0]
	}

	hc := &client.Config{
		ServerAddr:  serverAddr,
		Auth:        cfg.Auth,
		ConnFactory: &protectedConnFactory{},
	}
	sni := cfg.TLS.SNI
	if sni == "" {
		sni = host
	}
	hc.TLSConfig.ServerName = sni
	hc.TLSConfig.InsecureSkipVerify = cfg.TLS.Insecure

	if cfg.Bandwidth != nil {
		if up, e := parseBandwidth(cfg.Bandwidth.Up); e == nil {
			hc.BandwidthConfig.MaxTx = up
		}
		if down, e := parseBandwidth(cfg.Bandwidth.Down); e == nil {
			hc.BandwidthConfig.MaxRx = down
		}
	}
	return hc, nil
}

// parseBandwidth parses "100 mbps" / "50mbps" into bits per second.
func parseBandwidth(s string) (uint64, error) {
	s = strings.ToLower(strings.TrimSpace(s))
	if s == "" {
		return 0, fmt.Errorf("empty")
	}
	var mult uint64 = 1
	switch {
	case strings.HasSuffix(s, "gbps"), strings.HasSuffix(s, "g"):
		mult = 1000 * 1000 * 1000
		s = strings.TrimSuffix(strings.TrimSuffix(s, "gbps"), "g")
	case strings.HasSuffix(s, "mbps"), strings.HasSuffix(s, "m"):
		mult = 1000 * 1000
		s = strings.TrimSuffix(strings.TrimSuffix(s, "mbps"), "m")
	case strings.HasSuffix(s, "kbps"), strings.HasSuffix(s, "k"):
		mult = 1000
		s = strings.TrimSuffix(strings.TrimSuffix(s, "kbps"), "k")
	case strings.HasSuffix(s, "bps"):
		s = strings.TrimSuffix(s, "bps")
	}
	n, err := strconv.ParseUint(strings.TrimSpace(s), 10, 64)
	if err != nil {
		return 0, err
	}
	return n * mult, nil
}

func ensurePort(s string) string {
	if _, _, err := net.SplitHostPort(s); err != nil {
		return s + ":0"
	}
	return s
}
