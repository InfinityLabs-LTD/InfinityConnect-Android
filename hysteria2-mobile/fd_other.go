//go:build !(linux || android || darwin)

package hysteria2

import "errors"

// Заглушки для платформ, где обёртка не собирается в рабочий AAR (Windows —
// только для проверки типов в IDE/`go vet`). Целевая сборка — android/*.

func dupFD(fd int) (int, error) { return -1, errors.New("dupFD: unsupported platform") }

func closeFD(fd int) error { return errors.New("closeFD: unsupported platform") }
