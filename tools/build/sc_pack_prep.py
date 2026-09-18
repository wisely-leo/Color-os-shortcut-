# -*- coding: utf-8 -*-
"""
ShortcutBlur 打包辅助：
 1) 用 v70c9.apk 当模板，改写其 AndroidManifest.xml 里的包名/authority
    （原地缩短替换，不动字符串池偏移，绝对安全）
 2) 写入 META-INF/xposed/{java_init.list,module.prop,scope.list}

用法:
  python3 sc_pack_prep.py <模板apk> <工作目录> <输出apk>
"""
import sys, os, zipfile, shutil, struct, zlib

# 当作为模块被 import（如构建脚本复用 pack_aligned）时，不要求命令行参数
if len(sys.argv) >= 4:
    TPL   = sys.argv[1]      # 模板 apk（v70c9.apk）
    WORK  = sys.argv[2]      # 工作目录
    OUT   = sys.argv[3]      # 输出 apk
else:
    TPL = WORK = OUT = None

OLD_PKG  = "com.taskview.background.iconblur"
NEW_PKG  = "com.shortcutblur"
NEW_AUTH = NEW_PKG + ".XposedService"

MODULE_CLASS = NEW_PKG + ".ShortcutBlurModule"

# 最低/目标都定 Android 16（API 36）；maxSdk 不限（不写 maxSdkVersion）
SDK_MIN = 36
SDK_TARGET = 36
SDK_MIN_STR = "36"

MODULE_PROP = ("minApiVersion=101\n"
               "targetApiVersion=102\n"
               "staticScope=false\n"
               "exceptionMode=protective\n"
               "autoHotReload=false\n")

SCOPE = ("com.android.launcher\n"
         "com.oplus.launcher\n"
         "com.coloros.launcher\n")

# 图标资源目录（工程内 assets/icons/）
def _find_icon_dir():
    here = os.path.dirname(os.path.abspath(__file__))
    for up in (here, os.path.dirname(here), os.path.dirname(os.path.dirname(here))):
        cand = os.path.join(up, "assets", "icons")
        if os.path.isdir(cand):
            return cand
    return os.path.join(os.path.dirname(os.path.dirname(here)), "assets", "icons")


ICON_DIR = _find_icon_dir()

# 模板里 ic_launcher 出现的各密度目录 -> 新图标文件
ICON_MAP = {
    "res/mipmap-mdpi-v4/ic_launcher.png": "mipmap-mdpi-v4_ic_launcher.png",
    "res/mipmap-hdpi-v4/ic_launcher.png": "mipmap-hdpi-v4_ic_launcher.png",
    "res/mipmap-xhdpi-v4/ic_launcher.png": "mipmap-xhdpi-v4_ic_launcher.png",
    "res/mipmap-xxhdpi-v4/ic_launcher.png": "mipmap-xxhdpi-v4_ic_launcher.png",
    "res/mipmap-xxxhdpi-v4/ic_launcher.png": "mipmap-xxxhdpi-v4_ic_launcher.png",
}


def replace_icons(work_dir):
    """用工程 assets/icons/ 下的图标替换模板里各密度的 ic_launcher.png。
    若模板缺少某密度目录则自动创建（资源名不变，arsc 引用不受影响）。"""
    replaced = 0
    if not os.path.isdir(ICON_DIR):
        print("  [icon] assets/icons 不存在，跳过图标替换")
        return 0
    for rel, src_name in ICON_MAP.items():
        src = os.path.join(ICON_DIR, src_name)
        if not os.path.exists(src):
            continue
        dst = os.path.join(work_dir, rel)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copyfile(src, dst)
        replaced += 1
        print("  [icon] %s <- %s (%d bytes)" % (rel, src_name, os.path.getsize(src)))
    print("  [icon] replaced %d density variants" % replaced)
    return replaced


def pack_aligned(src_dir, out_path, exclude_sigs=True):
    """将 src_dir 打包成 out_path，保证 resources.arsc 为 STORED 且 4 字节对齐。
    Android 11+ (targetSdk>=30) 安装要求。可复用于构建脚本的任意重打包步骤。"""
    entries = []
    for root, dirs, files in os.walk(src_dir):
        dirs.sort(); files.sort()
        for fn in files:
            full = os.path.join(root, fn)
            rel = os.path.relpath(full, src_dir).replace(os.sep, "/")
            if exclude_sigs and rel.startswith("META-INF/") and (
                    rel.endswith(".RSA") or rel.endswith(".SF")
                    or rel == "META-INF/MANIFEST.MF"):
                continue
            entries.append((full, rel))

    STORED = {"resources.arsc"}
    ALIGN = {"resources.arsc"}

    def _dos_time(ts):
        import time as _t
        lt = _t.localtime(ts)
        dt = ((lt.tm_year - 1980) << 9) | (lt.tm_mon << 5) | lt.tm_mday
        tm = (lt.tm_hour << 11) | (lt.tm_min << 5) | (lt.tm_sec // 2)
        return dt, tm

    if os.path.exists(out_path):
        os.remove(out_path)

    with open(out_path, 'wb') as out:
        central = []
        for full, rel in entries:
            with open(full, 'rb') as f:
                raw = f.read()
            dt, tm = _dos_time(os.path.getmtime(full))
            name = rel.encode('utf-8')
            crc = zlib.crc32(raw) & 0xffffffff
            if rel in STORED:
                comp = raw; method = 0
            else:
                # ZIP 需要 raw DEFLATE（wbits=-15，无 zlib 头/adler32 尾）
                co = zlib.compressobj(9, zlib.DEFLATED, -15)
                comp = co.compress(raw) + co.flush()
                method = 8

            extra = b''
            if rel in ALIGN:
                for pad_inner in range(0, 4):
                    tot = out.tell() + 30 + len(name) + 4 + pad_inner
                    if tot % 4 == 0:
                        break
                extra = struct.pack('<HH', 0xCAFE, pad_inner) + b'\x00' * pad_inner
            extra_len = len(extra)

            data_off = out.tell() + 30 + len(name) + extra_len
            lfh_off = out.tell()
            out.write(struct.pack('<IHHHHHIIIHH', 0x04034b50, 20, 0, method, tm, dt,
                                  crc, len(comp), len(raw), len(name), extra_len))
            out.write(name); out.write(extra)
            assert out.tell() == data_off
            out.write(comp)
            central.append((name, method, tm, dt, crc, len(comp), len(raw), lfh_off))

        cd_start = out.tell()
        for name, method, tm, dt, crc, csize, usize, lfh_off in central:
            out.write(struct.pack('<IHHHHHHIIIHHHHHII', 0x02014b50, 20, 20, 0,
                                  method, tm, dt, crc, csize, usize, len(name),
                                  0, 0, 0, 0, 0, lfh_off))
            out.write(name)
        cd_size = out.tell() - cd_start
        out.write(struct.pack('<IHHHHIIH', 0x06054b50, 0, 0,
                              len(central), len(central), cd_size, cd_start, 0))
    return out_path


def patch_axml_strings(data, repl):
    """
    在二进制资源文件里就地替换字符串（只允许等长或缩短；缩短补 0x0000）。
    自动扫描定位 UTF-16 字符串池（兼容 AXML 与 resources.arsc）。
    """
    buf = bytearray(data)
    n_patched = 0

    def try_pool(sp_off):
        nonlocal n_patched
        st, shsz, ssz = struct.unpack('<HHI', bytes(buf[sp_off:sp_off+8]))
        if st != 0x0001:
            return False
        strcount, stylecount, flags, strstart, stylestart = struct.unpack(
            '<IIIII', bytes(buf[sp_off+8:sp_off+28]))
        utf8 = bool(flags & (1 << 8))
        if utf8:
            return False
        # 合理性校验
        if not (0 < strcount < 100000):
            return False
        base = sp_off + strstart
        if base + strcount * 4 > len(buf):
            return False
        try:
            offs = [struct.unpack('<I', bytes(buf[sp_off+28+i*4: sp_off+32+i*4]))[0]
                    for i in range(strcount)]
        except Exception:
            return False
        for i, o in enumerate(offs):
            p = base + o
            if p + 2 > len(buf):
                continue
            ln = struct.unpack('<H', bytes(buf[p:p+2]))[0]
            if p + 2 + ln*2 > len(buf):
                continue
            s = bytes(buf[p+2:p+2+ln*2]).decode('utf-16-le', errors='ignore')
            if s in repl:
                ns = repl[s]
                if len(ns) > ln:
                    raise RuntimeError("新串更长，无法原地替换: %r -> %r" % (s, ns))
                struct.pack_into('<H', buf, p, len(ns))
                enc = ns.encode('utf-16-le')
                buf[p+2:p+2+len(enc)] = enc
                for k in range(p + 2 + len(enc), p + 2 + ln*2):
                    buf[k] = 0
                n_patched += 1
                print("  [patch@%#x] %r -> %r (idx=%d)" % (sp_off, s, ns, i))
        return True

    # 定位字符串池：
    #   1) 先按 chunk 树遍历（最准）
    #   2) 兜底：4 字节对齐扫描
    candidates = []

    def collect_chunks(off):
        while off + 8 <= len(buf):
            try:
                t, hs, sz = struct.unpack('<HHI', bytes(buf[off:off+8]))
            except Exception:
                break
            if sz <= 0 or off + sz > len(buf):
                break
            if t == 0x0001:
                candidates.append(off)
            # 若为 RES_TABLE（0x0002），进入其内部 chunk
            if t == 0x0002:
                collect_chunks(off + hs)
            off += sz

    collect_chunks(0)

    # 兜底扫描
    for off in range(0, len(buf) - 28, 4):
        if buf[off:off+2] == b'\x01\x00' and off not in candidates:
            candidates.append(off)

    found = False
    for off in candidates:
        if try_pool(off):
            found = True
            # 不 break：继续尝试其他候选（不同文件的字符串池可能有多层）
    if not found:
        print("  [warn] 未找到字符串池")
    return bytes(buf), n_patched


def patch_axml_sdk(data, min_sdk, target_sdk, max_sdk=None):
    """
    修改 AXML AndroidManifest 里 <uses-sdk> 的 minSdkVersion / targetSdkVersion
    的整型 data 字段（TYPE_INT_DEC = 0x10）。

    ★ 关键：SDK 值是属性内联的 int data，不是字符串池文本。
      改字符串池里的 '14' 完全无效（那是 compileSdkVersionCodename /
      platformBuildVersionName）。必须改属性的 typedValue.data。

    属性 name 字段在本 APK 中存的是【字符串池索引】（非资源ID），
    因此按 tag=='uses-sdk' + name 字符串内容定位。

    返回 (new_bytes, changes)；changes[name] = (old, new, byte_off)
    """
    buf = bytearray(data)
    changes = {}
    if len(buf) < 8:
        return bytes(buf), changes
    t, hsz, sz = struct.unpack('<HHI', bytes(buf[:8]))
    if t != 0x0003:  # RES_XML_TYPE
        return bytes(buf), changes

    RES_STRING_POOL = 0x0001
    RES_XML_START_ELEMENT = 0x0102

    pos = hsz
    strs = []
    while pos + 8 <= len(buf):
        ct, chsz, csz = struct.unpack('<HHI', bytes(buf[pos:pos+8]))
        if csz <= 0 or pos + csz > len(buf):
            break
        if ct == RES_STRING_POOL:
            count, stylecount, flags, strstart, stylestart = struct.unpack(
                '<IIIII', bytes(buf[pos+8:pos+28]))
            base = pos + strstart
            utf8 = bool(flags & (1 << 8))
            offs = [struct.unpack('<I', bytes(buf[pos+28+i*4:pos+32+i*4]))[0]
                    for i in range(count)]
            strs = []
            for o in offs:
                p = base + o
                if utf8:
                    k = 0
                    while True:
                        b = buf[p+k]; k += 1
                        if not (b & 0x80):
                            break
                    nb = 0; sh = 0
                    while True:
                        b = buf[p+k]; k += 1
                        nb |= (b & 0x7f) << sh
                        if not (b & 0x80):
                            break
                        sh += 7
                    strs.append(bytes(buf[p+k:p+k+nb]).decode('utf-8', 'ignore'))
                else:
                    ln = struct.unpack('<H', bytes(buf[p:p+2]))[0]
                    strs.append(bytes(buf[p+2:p+2+ln*2]).decode('utf-16-le', 'ignore'))
        elif ct == RES_XML_START_ELEMENT:
            node = pos
            ns_i, name_i = struct.unpack('<II', bytes(buf[node+16:node+24]))
            a_start, a_size, a_count = struct.unpack('<HHH', bytes(buf[node+24:node+30]))
            tag = strs[name_i] if 0 <= name_i < len(strs) else str(name_i)
            if tag == 'uses-sdk' and a_size >= 20:
                attrs_base = node + 16 + a_start
                for k in range(a_count):
                    ap = attrs_base + k * a_size
                    anm = struct.unpack('<I', bytes(buf[ap+4:ap+8]))[0]
                    vtype = buf[ap+15]
                    vdata = struct.unpack('<I', bytes(buf[ap+16:ap+20]))[0]
                    name = strs[anm] if 0 <= anm < len(strs) else None
                    if name == 'minSdkVersion' and vtype == 0x10 and vdata != min_sdk:
                        struct.pack_into('<I', buf, ap+16, min_sdk)
                        changes['minSdkVersion'] = (vdata, min_sdk, ap+16)
                    elif name == 'targetSdkVersion' and vtype == 0x10 and vdata != target_sdk:
                        struct.pack_into('<I', buf, ap+16, target_sdk)
                        changes['targetSdkVersion'] = (vdata, target_sdk, ap+16)
                    elif name == 'maxSdkVersion' and max_sdk is not None:
                        if vtype == 0x10 and vdata != max_sdk:
                            struct.pack_into('<I', buf, ap+16, max_sdk)
                            changes['maxSdkVersion'] = (vdata, max_sdk, ap+16)
        pos += csz
    return bytes(buf), changes


def patch_arsc_package_name(data, old_pkg, new_pkg):
    """
    改写 resources.arsc 里 RES_TABLE_PACKAGE(0x0200) chunk 头的包名字段。
    该字段是定长 256 字节（128 个 UTF-16 槽位），缩短安全（剩余填 0）。
    """
    if len(new_pkg) > 128:
        raise RuntimeError("新包名超出 128 字符槽位")
    buf = bytearray(data)
    # 顶层 RES_TABLE(0x0002)，子 chunk 从 hsz 开始
    t, hsz, sz = struct.unpack('<HHI', bytes(buf[:8]))
    if t != 0x0002:
        return bytes(buf), 0
    off = hsz
    n = 0
    while off + 8 <= len(buf):
        ct, chsz, csz = struct.unpack('<HHI', bytes(buf[off:off+8]))
        if csz <= 0 or off + csz > len(buf):
            break
        if ct == 0x0200:  # RES_TABLE_PACKAGE
            name_off = off + 12
            cur = bytes(buf[name_off:name_off+256]).decode('utf-16-le', 'ignore').rstrip('\x00')
            if cur == old_pkg:
                enc = new_pkg.encode('utf-16-le')
                buf[name_off:name_off+len(enc)] = enc
                for k in range(name_off+len(enc), name_off+256):
                    buf[k] = 0
                n += 1
                print("  [arsc] package name: %r -> %r @%d" % (cur, new_pkg, name_off))
            break
        off += csz
    return bytes(buf), n


def main():
    if os.path.exists(WORK):
        shutil.rmtree(WORK)
    os.makedirs(WORK)

    # ---- 1) 解包模板 ----
    with zipfile.ZipFile(TPL, 'r') as z:
        z.extractall(WORK)

    # ---- 1b) 替换应用图标（安卓机器人：左清晰 + 右模糊）----
    replace_icons(WORK)

    # ---- 2) 改写 AndroidManifest ----
    mf = os.path.join(WORK, "AndroidManifest.xml")
    with open(mf, 'rb') as f:
        data = f.read()
    new_data, n = patch_axml_strings(data, {
        OLD_PKG: NEW_PKG,                          # 'com.taskview.background.iconblur'
        OLD_PKG + ".XposedService": NEW_AUTH,      # provider authorities
    })
    # ★ SDK 限制：改 uses-sdk 属性的整型 data（26->36, 34->36）。
    #   注意：绝不能靠改字符串池的 '14' —— 那是 compileSdkVersionCodename，
    #   与 minSdkVersion 无关（上一轮踩过的坑）。
    new_data, sdk_ch = patch_axml_sdk(new_data, SDK_MIN, SDK_TARGET, max_sdk=None)
    with open(mf, 'wb') as f:
        f.write(new_data)
    print("AndroidManifest patched, strings=%d, sdk=%s" % (n, sdk_ch))

    # ---- 2b) 改写 resources.arsc 里的资源包名 ----
    arsc = os.path.join(WORK, "resources.arsc")
    if os.path.exists(arsc):
        with open(arsc, 'rb') as f:
            ad = f.read()
        na, m = patch_arsc_package_name(ad, OLD_PKG, NEW_PKG)
        if m:
            with open(arsc, 'wb') as f:
                f.write(na)
        print("resources.arsc patched, count=%d" % m)

    # ---- 3) 写 xposed 元数据 ----
    mx = os.path.join(WORK, "META-INF", "xposed")
    os.makedirs(mx, exist_ok=True)
    with open(os.path.join(mx, "java_init.list"), 'w') as f:
        f.write(MODULE_CLASS + "\n")
    with open(os.path.join(mx, "module.prop"), 'w') as f:
        f.write(MODULE_PROP)
    with open(os.path.join(mx, "scope.list"), 'w') as f:
        f.write(SCOPE)
    print("meta written: java_init=%s" % MODULE_CLASS)

    # ---- 4) 重新打包（排除旧签名；resources.arsc STORED + 4 字节对齐）----
    pack_aligned(WORK, OUT)
    print("repacked -> %s (%d bytes)" % (OUT, os.path.getsize(OUT)))
    # 校验 alignment + 存储方式
    with open(OUT, 'rb') as f:
        blob = f.read()
    pos = 0
    while pos < len(blob) - 4:
        if blob[pos:pos+4] == b'PK\x03\x04':
            nlen = struct.unpack('<H', blob[pos+26:pos+28])[0]
            elen = struct.unpack('<H', blob[pos+28:pos+30])[0]
            m = struct.unpack('<H', blob[pos+8:pos+10])[0]
            nm = blob[pos+30:pos+30+nlen].decode('utf-8', 'replace')
            if nm == 'resources.arsc':
                doff = pos + 30 + nlen + elen
                print("  resources.arsc: method=%d offset=%d align4=%s" % (
                    m, doff, doff % 4 == 0))
                break
            csz = struct.unpack('<I', blob[pos+18:pos+22])[0]
            pos = pos + 30 + nlen + elen + csz
        else:
            pos += 1


if __name__ == "__main__":
    main()
