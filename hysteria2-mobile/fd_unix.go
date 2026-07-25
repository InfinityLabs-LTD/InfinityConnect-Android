//go:build linux || android || darwin

package hysteria2

import "golang.org/x/sys/unix"

// dupFD дублирует файловый дескриптор: копия ссылается на то же описание
// открытого файла, но закрывается независимо.
//
// Нужно из-за разделённого владения TUN fd между Java и Go. Ядру приложение
// передаёт ОРИГИНАЛЬНЫЙ дескриптор VpnService (на копии libv2ray не получает
// обратный трафик), а sing-tun оборачивает полученный fd в os.NewFile и
// закрывает его в tunIf.Close(). Копию для себя поэтому делает тот, кто её
// закрывает, — эта обёртка; иначе fd закрыли бы дважды, а это на Android 12+
// ловит fdsan в libc и роняет процесс (SIGABRT).
//
// Копия помечается FD_CLOEXEC — дочерние процессы TUN-дескриптор наследовать
// не должны.
func dupFD(fd int) (int, error) {
	newFd, err := unix.Dup(fd)
	if err != nil {
		return -1, err
	}
	// Ошибку выставления CLOEXEC не считаем фатальной: дескриптор рабочий,
	// а форк-exec в мобильном клиенте не используется.
	_, _ = unix.FcntlInt(uintptr(newFd), unix.F_SETFD, unix.FD_CLOEXEC)
	return newFd, nil
}

// closeFD закрывает дескриптор, полученный из [dupFD]. Нужен на путях
// аварийного выхода из NewTunnel, пока копией ещё не завладел sing-tun.
func closeFD(fd int) error {
	return unix.Close(fd)
}
