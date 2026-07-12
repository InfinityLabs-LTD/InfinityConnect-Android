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

func protectControl(_, _ string, c syscall.RawConn) error {
	p := currentProtector()
	if p == nil {
		return nil
	}
	return c.Control(func(fd uintptr) { p.Protect(int(fd)) })
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
