module github.com/infinityconnect/hysteria2mobile

go 1.25.0

require (
	github.com/apernet/hysteria/core/v2 v2.0.0
	github.com/apernet/sing-tun v0.2.6-0.20250920121535-299f04629986
	github.com/sagernet/sing v0.3.2
	golang.org/x/mobile v0.0.0-20260709172247-6129f5bee9d5
	golang.org/x/sys v0.47.0
)

require (
	github.com/apernet/quic-go v0.60.1-0.20260618182935-599b15a1fa26 // indirect
	github.com/davecgh/go-spew v1.1.1 // indirect
	github.com/fsnotify/fsnotify v1.7.0 // indirect
	github.com/go-ole/go-ole v1.3.0 // indirect
	github.com/pmezard/go-difflib v1.0.0 // indirect
	github.com/quic-go/qpack v0.6.0 // indirect
	github.com/sagernet/netlink v0.0.0-20220905062125-8043b4a9aa97 // indirect
	github.com/scjalliance/comshim v0.0.0-20230315213746-5e51f40bd3b9 // indirect
	github.com/stretchr/objx v0.5.2 // indirect
	github.com/stretchr/testify v1.11.1 // indirect
	github.com/vishvananda/netns v0.0.0-20211101163701-50045581ed74 // indirect
	go4.org/netipx v0.0.0-20231129151722-fdeea329fbba // indirect
	golang.org/x/crypto v0.54.0 // indirect
	golang.org/x/exp v0.0.0-20240506185415-9bf2ced13842 // indirect
	golang.org/x/mod v0.38.0 // indirect
	golang.org/x/net v0.57.0 // indirect
	golang.org/x/sync v0.22.0 // indirect
	golang.org/x/text v0.40.0 // indirect
	golang.org/x/tools v0.48.0 // indirect
	gopkg.in/yaml.v3 v3.0.1 // indirect
)

replace github.com/apernet/hysteria/core/v2 => ../hysteria/core

replace github.com/apernet/hysteria/extras/v2 => ../hysteria/extras
