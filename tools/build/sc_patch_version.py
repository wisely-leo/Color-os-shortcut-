# -*- coding: utf-8 -*-
import struct, sys

def u16(buf, o):
    hi = struct.unpack_from('<H', buf, o)[0]
    if hi == 0: return ''
    ln = hi; st = o+2
    if ln >= 0x8000:
        ln = ((hi & 0x7fff) << 16)
        lo = struct.unpack_from('<H', buf, st)[0]; ln |= lo; st += 2
    return buf[st:st+ln*2].decode('utf-16-le','replace')

def pool_str(buf, pool_off, idx):
    strcount, stylecount, flags, strstart, stylestart = struct.unpack_from('<IIIII', buf, pool_off+8)
    if idx >= strcount: return None, None
    off = struct.unpack_from('<I', buf, pool_off+28+idx*4)[0]
    pos = pool_off + strstart + off
    return pos, u16(buf, pos)

def set_pool_str(buf, pool_off, idx, new):
    pos, old = pool_str(buf, pool_off, idx)
    if old is None: raise RuntimeError('idx out of range')
    oldlen = struct.unpack_from('<H', buf, pos)[0]
    if len(new) > oldlen:
        raise RuntimeError('new longer: %r -> %r' % (old, new))
    struct.pack_into('<H', buf, pos, len(new))
    enc = new.encode('utf-16-le')
    buf[pos+2:pos+2+len(enc)] = enc
    for k in range(pos+2+len(enc), pos+2+oldlen*2):
        buf[k] = 0
    return old

def find_pool(buf):
    t, h, sz = struct.unpack_from('<HHI', buf, 0)
    return h

def main(path, vc_new, vn_new, label_new):
    data = bytearray(open(path,'rb').read())
    pool = find_pool(data)
    print('pool_off=%d' % pool)
    # 1) versionName string  +  2) label string
    # locate indices by matching content dynamically
    strcount = struct.unpack_from('<I', data, pool+8)[0]
    idx_vn = idx_label = None
    for i in range(strcount):
        _, s = pool_str(data, pool, i)
        if s == '1.0': idx_vn = i
        if s == 'taskview background iconblur': idx_label = i
    print('idx versionName=%s label=%s' % (idx_vn, idx_label))
    if idx_vn is not None:
        o = set_pool_str(data, pool, idx_vn, vn_new)
        print('  versionName: %r -> %r (idx=%d)' % (o, vn_new, idx_vn))
    if idx_label is not None:
        o = set_pool_str(data, pool, idx_label, label_new)
        print('  label: %r -> %r (idx=%d)' % (o, label_new, idx_label))
    # 3) versionCode int in <manifest> attr
    pos = pool + struct.unpack_from('<I', data, pool+4)[0]
    patched_vc = False
    while pos + 8 <= len(data):
        ctype, chdr, csize = struct.unpack_from('<HHI', data, pos)
        if ctype == 0x0102:
            nsIdx, nameIdx = struct.unpack_from('<ii', data, pos+16)
            attrStart, attrSize, attrCount = struct.unpack_from('<HHH', data, pos+24)
            ab = pos + 16 + attrStart
            for k in range(attrCount):
                p = ab + k*attrSize
                ans, anm, rawv = struct.unpack_from('<iii', data, p)
                tsz, res0, dt = struct.unpack_from('<HBB', data, p+12)
                nm = pool_str(data, pool, anm)[1]
                if nm == 'versionCode' and dt == 0x10:
                    old = struct.unpack_from('<i', data, p+16)[0]
                    struct.pack_into('<i', data, p+16, vc_new)
                    print('  versionCode: %d -> %d @0x%04x' % (old, vc_new, p+16))
                    patched_vc = True
        pos += csize
    if not patched_vc:
        print('  [warn] versionCode not patched')
    open(path,'wb').write(data)
    print('DONE')

if __name__ == '__main__':
    main(sys.argv[1], int(sys.argv[2]), sys.argv[3], sys.argv[4])
