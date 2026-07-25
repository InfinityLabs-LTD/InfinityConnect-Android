package hysteria2

import (
	"context"
	"net"
	"syscall"
	"time"
)

// directDialer открывает прямые (мимо VPN) соединения: сокет защищается через
// Protector (VpnService.protect), иначе трафик зациклится обратно в TUN.
type directDialer struct{}

// protectFD зовёт Java-колбэк Protector.Protect на ОТДЕЛЬНОЙ горутине и ждёт
// результата.
//
// Почему не напрямую: Protect — это вызов Java из Go (cgo-callback). Внутри
// raw.Control (и вообще на пути, начатом экспортированной gomobile-функцией
// вроде NewTunnel) горутина сидит на системном стеке cgo-вызова; вложенный
// callback оттуда — та же схема, что уже ломала процесс на OnStatus/OnShutdown
// ("unexpected return pc for runtime.cgocallback"). Свежая горутина начинается
// с обычного Go-стека, поэтому callback безопасен. Синхронность сохраняем: fd
// обязан быть защищён до того, как сокет пойдёт в дело.
//
// Это профилактика: корневой причиной падений при переключении ядер была
// коллизия символов cgo-рантайма между двумя gomobile-AAR (лечится
// version script'ом в apply-toolchain-patch.sh), но вложенных Java-колбэков
// на горячем пути стоит избегать в любом случае.
func protectFD(fd uintptr) {
	p := currentProtector()
	if p == nil {
		return
	}
	done := make(chan struct{})
	go func() {
		defer close(done)
		defer func() { _ = recover() }()
		p.Protect(int(fd))
	}()
	<-done
}

func protectControl(_, _ string, c syscall.RawConn) error {
	if currentProtector() == nil {
		return nil
	}
	return c.Control(func(fd uintptr) { protectFD(fd) })
}

func (d *directDialer) dialTCP(ctx context.Context, addr string) (net.Conn, error) {
	nd := &net.Dialer{
		Timeout: 15 * time.Second,
		Control: protectControl,
	}
	return nd.DialContext(ctx, "tcp", addr)
}

func (d *directDialer) dialUDP() (*net.UDPConn, error) {
	lc := &net.ListenConfig{Control: protectControl}
	pc, err := lc.ListenPacket(context.Background(), "udp", "")
	if err != nil {
		return nil, err
	}
	return pc.(*net.UDPConn), nil
}
