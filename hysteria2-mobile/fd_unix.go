//go:build linux || android || darwin

package hysteria2

import "golang.org/x/sys/unix"

// closeFD закрывает файловый дескриптор.
//
// Нужен на путях аварийного выхода из NewTunnel: TUN fd передаётся обёртке во
// владение (см. комментарий в hysteria2.go), поэтому если туннель поднять не
// удалось до того, как дескриптором завладел sing-tun, закрыть его обязаны мы —
// иначе он утечёт до перезапуска процесса.
func closeFD(fd int) error {
	return unix.Close(fd)
}
