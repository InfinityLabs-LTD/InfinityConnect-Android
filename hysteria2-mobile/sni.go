package hysteria2

import (
	"encoding/binary"
	"errors"
	"io"
	"net"
	"time"
)

// peekedConn оборачивает net.Conn, возвращая уже прочитанные байты первым.
type peekedConn struct {
	net.Conn
	peeked []byte
}

func (c *peekedConn) Read(b []byte) (int, error) {
	if len(c.peeked) > 0 {
		n := copy(b, c.peeked)
		c.peeked = c.peeked[n:]
		return n, nil
	}
	return c.Conn.Read(b)
}

// sniffTLSHost читает начало соединения и извлекает SNI из TLS ClientHello.
// Возвращает обёрнутый conn (с «возвращёнными» прочитанными байтами) и host
// (пустой, если не TLS/не найдено). Таймаут короткий — не блокируем не-TLS.
func sniffTLSHost(conn net.Conn) (net.Conn, string) {
	_ = conn.SetReadDeadline(time.Now().Add(2 * time.Second))
	defer conn.SetReadDeadline(time.Time{})

	// TLS record header: 5 байт (type=22 handshake, version, length).
	header := make([]byte, 5)
	if _, err := io.ReadFull(conn, header); err != nil {
		return &peekedConn{Conn: conn, peeked: header[:0]}, ""
	}
	if header[0] != 0x16 { // не handshake — не TLS
		return &peekedConn{Conn: conn, peeked: header}, ""
	}
	recLen := int(binary.BigEndian.Uint16(header[3:5]))
	if recLen <= 0 || recLen > 16384 {
		return &peekedConn{Conn: conn, peeked: header}, ""
	}
	body := make([]byte, recLen)
	if _, err := io.ReadFull(conn, body); err != nil {
		return &peekedConn{Conn: conn, peeked: append(header, body...)}, ""
	}
	host, _ := parseSNI(body)
	return &peekedConn{Conn: conn, peeked: append(header, body...)}, host
}

// parseSNI разбирает TLS ClientHello (handshake body) и достаёт server_name.
func parseSNI(b []byte) (string, error) {
	// handshake: type(1)=1 ClientHello, length(3), version(2), random(32)
	if len(b) < 38 || b[0] != 0x01 {
		return "", errors.New("not client hello")
	}
	pos := 38
	// session_id
	if pos >= len(b) {
		return "", io.ErrUnexpectedEOF
	}
	sidLen := int(b[pos])
	pos += 1 + sidLen
	// cipher_suites
	if pos+2 > len(b) {
		return "", io.ErrUnexpectedEOF
	}
	csLen := int(binary.BigEndian.Uint16(b[pos : pos+2]))
	pos += 2 + csLen
	// compression_methods
	if pos+1 > len(b) {
		return "", io.ErrUnexpectedEOF
	}
	cmLen := int(b[pos])
	pos += 1 + cmLen
	// extensions
	if pos+2 > len(b) {
		return "", io.ErrUnexpectedEOF
	}
	extTotal := int(binary.BigEndian.Uint16(b[pos : pos+2]))
	pos += 2
	end := pos + extTotal
	if end > len(b) {
		end = len(b)
	}
	for pos+4 <= end {
		extType := binary.BigEndian.Uint16(b[pos : pos+2])
		extLen := int(binary.BigEndian.Uint16(b[pos+2 : pos+4]))
		pos += 4
		if pos+extLen > end {
			break
		}
		if extType == 0x0000 { // server_name
			return parseServerNameExt(b[pos : pos+extLen])
		}
		pos += extLen
	}
	return "", errors.New("no sni")
}

func parseServerNameExt(b []byte) (string, error) {
	// server_name_list length(2), then entries: type(1)=0 host_name, len(2), name
	if len(b) < 2 {
		return "", io.ErrUnexpectedEOF
	}
	pos := 2
	for pos+3 <= len(b) {
		nameType := b[pos]
		nameLen := int(binary.BigEndian.Uint16(b[pos+1 : pos+3]))
		pos += 3
		if pos+nameLen > len(b) {
			break
		}
		if nameType == 0x00 {
			return string(b[pos : pos+nameLen]), nil
		}
		pos += nameLen
	}
	return "", errors.New("no host_name")
}
