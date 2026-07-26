package hysteria2

import (
	"net"

	"github.com/sagernet/sing/common/control"
)

// interfaceFinder сопоставляет сетевые интерфейсы по имени и индексу.
//
// Системный стек sing-tun использует его внутри (в частности при разборе
// принадлежности пакетов и при bind'е форвардера). Без него часть путей стека
// получает индекс -1 и молча отбрасывает пакеты — снаружи это выглядит как
// «соединения устанавливаются, но данные не доходят».
//
// Реализация повторяет эталонную из самой hysteria
// (app/internal/tun/server.go): стандартного net.Interface* достаточно,
// специфики Android здесь нет.
type interfaceFinder struct{}

var _ control.InterfaceFinder = (*interfaceFinder)(nil)

func (f *interfaceFinder) InterfaceIndexByName(name string) (int, error) {
	ifce, err := net.InterfaceByName(name)
	if err != nil {
		return -1, err
	}
	return ifce.Index, nil
}

func (f *interfaceFinder) InterfaceNameByIndex(index int) (string, error) {
	ifce, err := net.InterfaceByIndex(index)
	if err != nil {
		return "", err
	}
	return ifce.Name, nil
}
