package hysteria2

import (
	"net"
)

// protectedConnFactory creates the UDP socket that carries the client's QUIC
// traffic and hands its fd to the platform Protector (VpnService.protect) so the
// socket bypasses the TUN. Without this the client's own packets would be routed
// back into the TUN and the tunnel would deadlock.
type protectedConnFactory struct{}

func (f *protectedConnFactory) New(_ net.Addr) (net.PacketConn, error) {
	conn, err := net.ListenUDP("udp", nil)
	if err != nil {
		return nil, err
	}
	if p := currentProtector(); p != nil {
		if raw, cErr := conn.SyscallConn(); cErr == nil {
			_ = raw.Control(func(fd uintptr) {
				p.Protect(int(fd))
			})
		}
	}
	return conn, nil
}
