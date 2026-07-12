package hysteria2

import (
	"encoding/json"
	"net"
	"net/netip"
	"strings"
)

// routingConfig — контракт с Kotlin-стороной (Hysteria2ConfigBuilder добавляет
// поле "routing" в JSON конфига клиента). Отражает режим и правила из
// RoutingSettings приложения.
type routingConfig struct {
	Mode string `json:"mode"` // "ALL" | "BYPASS_RU" | "CUSTOM"
	// Правила для CUSTOM: массив объектов Xray-routing (используем domain/ip +
	// outboundTag). Домены сопоставляются по SNI (для TCP), ip — по адресу.
	Rules []routingRule `json:"rules"`
}

type routingRule struct {
	OutboundTag string   `json:"outboundTag"`
	Domain      []string `json:"domain"`
	IP          []string `json:"ip"`
}

// decision — куда направить поток.
type decision int

const (
	routeProxy  decision = iota // через hysteria-клиент
	routeDirect                 // напрямую, мимо VPN (protected socket)
)

// router принимает решение proxy/direct по адресу назначения и (опц.) домену.
type router struct {
	mode     string
	directIP []netip.Prefix // ip-префиксы → direct
	proxyIP  []netip.Prefix // ip-префиксы → proxy (приоритетнее direct при CUSTOM)
	// суффиксы доменов → direct (например "vk.com", ".ru")
	directDomainSuffix []string
	// нужно ли пытаться доставать SNI (есть доменные правила).
	sniffDomains bool
}

func newRouter(cfg *routingConfig) *router {
	r := &router{mode: cfg.Mode}
	switch cfg.Mode {
	case "BYPASS_RU":
		r.directDomainSuffix = append(r.directDomainSuffix, ruDomainSuffixes()...)
		r.directIP = append(r.directIP, ruIPPrefixes()...)
		r.sniffDomains = true
	case "CUSTOM":
		for _, rule := range cfg.Rules {
			direct := rule.OutboundTag == "direct" || rule.OutboundTag == "block"
			for _, d := range rule.Domain {
				suf := normalizeDomainRule(d)
				if suf == "" {
					continue
				}
				if direct {
					r.directDomainSuffix = append(r.directDomainSuffix, suf)
					r.sniffDomains = true
				}
			}
			for _, ipStr := range rule.IP {
				p := parsePrefix(ipStr)
				if !p.IsValid() {
					continue
				}
				if direct {
					r.directIP = append(r.directIP, p)
				} else {
					r.proxyIP = append(r.proxyIP, p)
				}
			}
		}
	default: // ALL — всё в proxy
	}
	return r
}

// routeByIP решает по IP назначения (домен неизвестен).
func (r *router) routeByIP(addr netip.Addr) decision {
	if !addr.IsValid() {
		return routeProxy
	}
	for _, p := range r.proxyIP {
		if p.Contains(addr) {
			return routeProxy
		}
	}
	for _, p := range r.directIP {
		if p.Contains(addr) {
			return routeDirect
		}
	}
	return routeProxy
}

// routeByDomain решает по домену (из SNI). Пустой домен → нет решения (proxy).
func (r *router) routeByDomain(host string) decision {
	if host == "" {
		return routeProxy
	}
	h := strings.ToLower(strings.TrimSuffix(host, "."))
	for _, suf := range r.directDomainSuffix {
		if matchDomainSuffix(h, suf) {
			return routeDirect
		}
	}
	return routeProxy
}

// hostFromAddr достаёт IP из "host:port"; возвращает невалидный Addr для домена.
func hostFromAddr(addr string) netip.Addr {
	host, _, err := net.SplitHostPort(addr)
	if err != nil {
		host = addr
	}
	a, err := netip.ParseAddr(host)
	if err != nil {
		return netip.Addr{}
	}
	return a
}

func matchDomainSuffix(host, suffix string) bool {
	if strings.HasPrefix(suffix, ".") {
		return strings.HasSuffix(host, suffix) || host == strings.TrimPrefix(suffix, ".")
	}
	return host == suffix || strings.HasSuffix(host, "."+suffix)
}

// normalizeDomainRule приводит Xray-правило домена к суффиксу:
//
//	"domain:vk.com" -> "vk.com"; "regexp:.*\.ru$" -> ".ru";
//	"full:ya.ru" -> "ya.ru"; "vk.com" -> "vk.com".
func normalizeDomainRule(rule string) string {
	rule = strings.TrimSpace(strings.ToLower(rule))
	switch {
	case strings.HasPrefix(rule, "domain:"):
		return strings.TrimPrefix(rule, "domain:")
	case strings.HasPrefix(rule, "full:"):
		return strings.TrimPrefix(rule, "full:")
	case strings.HasPrefix(rule, "regexp:"):
		// Достаём зону вида .ru/.su/.рф из regexp ".*\.ru$".
		re := strings.TrimPrefix(rule, "regexp:")
		re = strings.TrimSuffix(re, "$")
		if i := strings.LastIndex(re, "\\."); i >= 0 {
			return "." + re[i+2:]
		}
		return ""
	case strings.HasPrefix(rule, "geosite:"):
		return "" // geosite не поддерживаем на TUN-слое
	default:
		return rule
	}
}

func parsePrefix(s string) netip.Prefix {
	s = strings.TrimSpace(s)
	if strings.HasPrefix(s, "geoip:") {
		return netip.Prefix{} // geoip-теги на TUN-слое не резолвим
	}
	if p, err := netip.ParsePrefix(s); err == nil {
		return p
	}
	if a, err := netip.ParseAddr(s); err == nil {
		return netip.PrefixFrom(a, a.BitLen())
	}
	return netip.Prefix{}
}

// parseRoutingConfig извлекает routingConfig из общего JSON конфига клиента.
func parseRoutingConfig(raw string) *routingConfig {
	var wrapper struct {
		Routing *routingConfig `json:"routing"`
	}
	if err := json.Unmarshal([]byte(raw), &wrapper); err != nil || wrapper.Routing == nil {
		return &routingConfig{Mode: "ALL"}
	}
	if wrapper.Routing.Mode == "" {
		wrapper.Routing.Mode = "ALL"
	}
	return wrapper.Routing
}
