//go:build !(linux || android || darwin)

package hysteria2

import "errors"

// Заглушка для платформ, где обёртка не собирается в рабочий AAR (Windows —
// только для проверки типов в IDE/`go vet`). Целевая сборка — android/*.

func closeFD(fd int) error { return errors.New("closeFD: unsupported platform") }
