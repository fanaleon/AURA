"""Genera las imágenes de la modelo animada a partir de la foto original.

    python3 tools/make_head_assets.py [foto] [carpeta de salida] [carpeta de control]

Sin argumentos usa tools/modelo_original.jpg y escribe en app/src/main/res/drawable-nodpi:
   avatar_full.jpg : cuerpo entero, con aire agregado arriba de la cabeza (850 x 1915)
   head.jpg        : la cabeza al doble de resolución y SIN el micrófono (se reconstruye la piel y el labio de atrás)
   head_mic.png    : el micrófono recortado, para dibujarlo encima fijo a la cabeza

Los números de este archivo (dónde está el micrófono, la boca, etc.) valen para esta foto y van de la
mano con los de FaceMap en FaceRig.kt. Necesita opencv-python y numpy."""
import cv2, numpy as np, os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
SRC = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "modelo_original.jpg")
RES = sys.argv[2] if len(sys.argv) > 2 else os.path.join(HERE, "..", "app", "src", "main", "res", "drawable-nodpi")
CHECK = sys.argv[3] if len(sys.argv) > 3 else None     # si se pasa, guarda ahí imágenes para revisar a ojo
im = cv2.imread(SRC)
assert im is not None and im.shape[:2] == (1655, 893), "se esperaba la foto original de 893 x 1655"
h, w = im.shape[:2]
PAD, CLEAN = 260, 34
strip = im[0:CLEAN]
ext = cv2.resize(strip, (w, PAD + CLEAN), interpolation=cv2.INTER_CUBIC)
ext = cv2.GaussianBlur(ext, (0, 0), 7)
pad = np.concatenate([ext[:PAD], im], axis=0)
a = np.linspace(1.0, 0.0, CLEAN).reshape(-1, 1, 1)
pad[PAD:PAD+CLEAN] = (ext[PAD:PAD+CLEAN] * a + im[0:CLEAN] * (1 - a)).astype(np.uint8)
full = pad[:, 30:880].copy()                       # 850 x 1915: coordenadas de avatar_full
assert full.shape[:2] == (1915, 850), full.shape
cv2.imwrite(os.path.join(RES, "avatar_full.jpg"), full, [cv2.IMWRITE_JPEG_QUALITY, 93])

# --- cabeza al doble de resolución
HX, HY, HW, HH, Z = 318, 294, 222, 264, 2          # región de la cabeza en avatar_full y factor de la textura
head1 = full[HY:HY+HH, HX:HX+HW]
head = cv2.resize(head1, (HW*Z, HH*Z), interpolation=cv2.INTER_CUBIC)
def sharpen(im_):                                   # un poco de nitidez; se aplica al final, ya sin el micrófono,
    f = im_.astype(np.float32)                      # para que su borde negro no deje un halo claro en la piel
    return np.clip(f * 1.35 - cv2.GaussianBlur(f, (0, 0), 1.6) * 0.35, 0, 255)

def T(x, y): return ((x - HX) * Z, (y - HY) * Z)    # avatar_full -> textura

# --- máscara del micrófono (negro neutro sobre la cara)
b, g, r = [head[:, :, i].astype(np.int32) for i in range(3)]
mx = np.maximum(np.maximum(r, g), b)
dark = (mx < 92) & ((r - b) < 28)
box = np.zeros(dark.shape, bool)
x0, y0 = T(384, 469); x1, y1 = T(477, 503)
box[y0:y1, x0:x1] = True
core = (dark & box).astype(np.uint8)
core[:, :int(T(386.5, 0)[0])] = 0                      # más a la izquierda ya es pelo, no micrófono
core = cv2.morphologyEx(core, cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))
cx, cy = T(422.6, 487.6)
CAP_R = 12.6 * Z
disk = np.zeros_like(core); cv2.circle(disk, (int(round(cx)), int(round(cy))), int(round(CAP_R)), 1, -1)   # la cápsula entera, con su brillo
core = cv2.morphologyEx(core | disk, cv2.MORPH_CLOSE, np.ones((5, 5), np.uint8))
# solo las piezas del micrófono: cápsula, brazo izquierdo y espuma derecha (no los mechones de pelo oscuros)
n, lab, stats, _ = cv2.connectedComponentsWithStats(core, 8)
keep = np.zeros_like(core)
for sx_, sy_ in [(422.6, 487.6), (396, 484.3), (455, 490)]:
    tx_, ty_ = T(sx_, sy_); k_ = lab[int(ty_), int(tx_)]
    assert k_ != 0, ("semilla fuera del micrófono", sx_, sy_)
    keep[lab == k_] = 1
dist = cv2.distanceTransform((1 - keep).astype(np.uint8), cv2.DIST_L2, 5)             # distancia al micrófono, en px de textura
tw = np.clip((7.0 - dist) / 4.0, 0, 1); tw = tw * tw * (3 - 2 * tw)                    # 1 sobre el micrófono y su halo, 0 a 3,5 px
m1 = (dist <= 9.0).astype(np.uint8)                                                    # zona a reconstruir, con margen
# --- piel detrás del micrófono: en cada columna, degradé entre la piel de arriba y la de abajo
clean = head.astype(np.float32).copy()
Hh, Ww = m1.shape
topcol = np.zeros_like(clean)
for x in range(Ww):
    col = m1[:, x]
    y = 0
    while y < Hh:
        if col[y]:
            y0 = y
            while y < Hh and col[y]: y += 1
            y1 = y                                   # tramo [y0, y1)
            top = clean[max(0, y0-5):max(1, y0-1), x].mean(axis=0) if y0 > 1 else None
            bot = clean[min(Hh-1, y1+1):min(Hh, y1+5), x].mean(axis=0) if y1 < Hh - 1 else None
            if top is None: top = bot
            if bot is None: bot = top
            n_ = y1 - y0
            for k in range(n_):
                t = (k + 0.5) / n_
                t = t * t * (3 - 2 * t)
                clean[y0 + k, x] = top * (1 - t) + bot * t
                topcol[y0 + k, x] = top
        else:
            y += 1
sm = cv2.GaussianBlur(clean, (0, 0), 3.0)
wgt = cv2.GaussianBlur(m1.astype(np.float32), (0, 0), 1.0)[:, :, None]
clean = clean * (1 - wgt) + sm * wgt                 # suaviza solo lo reconstruido

# --- labio inferior detrás de la cápsula: se copia el color del pedacito que sí se ve
SEAM = [(407.5, 478.1), (410, 478.6), (416, 479.0), (423, 478.6), (430, 477.6), (436, 477.0), (442, 477.4), (448, 478.2), (453, 478.4)]
def seam_y(x):
    for (xa, ya), (xb, yb) in zip(SEAM, SEAM[1:]):
        if xa <= x <= xb: return ya + (yb - ya) * (x - xa) / (xb - xa)
    return SEAM[0][1] if x < SEAM[0][0] else SEAM[-1][1]
LIP_X0, LIP_X1, LIP_CX, LIP_T = 408.0, 450.0, 429.0, 13.0
ref_x = 440.0
ref_thick = LIP_T * (1 - ((ref_x - LIP_CX) / (LIP_X1 - LIP_CX)) ** 2) ** 0.55
prof = []                                            # perfil vertical de color del labio real, en x = 440
for k in range(64):
    yy = seam_y(ref_x) + 0.6 + ref_thick * k / 63.0
    tx, ty = T(ref_x, yy)
    prof.append(head[int(round(ty)), int(round(tx))-1:int(round(tx))+2].astype(np.float32).mean(axis=0))
prof = cv2.GaussianBlur(np.array(prof, np.float32).reshape(-1, 1, 3), (1, 0), 0, sigmaY=2.0).reshape(-1, 3)
lip = clean.copy(); lipa = np.zeros((Hh, Ww), np.float32)
for ty in range(Hh):
    for tx in range(Ww):
        if not m1[ty, tx]: continue
        x = HX + (tx + 0.5) / Z; y = HY + (ty + 0.5) / Z
        if not (LIP_X0 < x < LIP_X1): continue
        half = (LIP_X1 - LIP_CX) if x > LIP_CX else (LIP_CX - LIP_X0)
        thick = LIP_T * max(0.0, 1 - ((x - LIP_CX) / half) ** 2) ** 0.55
        d = y - seam_y(x)
        if thick < 1.0 or d > thick + 1.0: continue
        if d < 0.0:                                   # arriba de la unión: sigue el labio de arriba
            lip[ty, tx] = topcol[ty, tx]; lipa[ty, tx] = min(1.0, (thick - 1.0) / 2.0)
            continue
        f = min(max(d / thick, 0.0), 1.0)
        c = prof[int(round(f * 63))]
        edge = min(1.0, (thick + 1.0 - d) / 2.0, (thick - 1.0) / 2.0)
        lip[ty, tx] = c; lipa[ty, tx] = max(0.0, edge)
lipa = cv2.GaussianBlur(lipa, (0, 0), 0.8)[:, :, None]
clean = clean * (1 - lipa) + lip * lipa
# Debajo del labio (mentón y mejillas) no hay detalle que cuidar: se borran las rayitas oscuras que
# quedan de los bordes del micrófono y se empareja la piel reconstruida.
yy, xx = np.mgrid[0:Hh, 0:Ww]
ysrc = HY + (yy + 0.5) / Z; xsrc = HX + (xx + 0.5) / Z
seam_row = np.vectorize(seam_y)(HX + (np.arange(Ww) + 0.5) / Z)
below = (ysrc > seam_row[None, :] + LIP_T + 1.5) | (xsrc < LIP_X0 - 2) | (xsrc > LIP_X1 + 2)
below = cv2.GaussianBlur((below & (m1 == 1)).astype(np.float32), (0, 0), 1.5)[:, :, None]
closed = cv2.morphologyEx(clean, cv2.MORPH_CLOSE, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (9, 9)))
closed = cv2.GaussianBlur(closed, (0, 0), 2.2)
clean = clean * (1 - below) + closed * below

# El micrófono es negro: donde la foto es más clara que lo reconstruido, lo que se ve es piel o labio
# de verdad y se deja la foto. Así la capa del micrófono solo tiene que oscurecer.
lum_ = lambda im_: im_[:, :, 2] * 0.299 + im_[:, :, 1] * 0.587 + im_[:, :, 0] * 0.114
real = np.clip((lum_(head.astype(np.float32)) - lum_(clean) - 3.0) / 9.0, 0, 1)
real = real * real * (3 - 2 * real)
real[cv2.dilate(keep, np.ones((3, 3), np.uint8)) == 1] = 0                            # sobre el micrófono, siempre lo reconstruido
real = cv2.GaussianBlur(real, (0, 0), 1.6)[:, :, None]
clean = clean * (1 - real) + head.astype(np.float32) * real
clean = clean * tw[:, :, None] + head.astype(np.float32) * (1 - tw[:, :, None])      # lejos del micrófono queda la foto tal cual
clean = np.clip(clean, 0, 255).round().astype(np.uint8)
# --- capa del micrófono: todo lo que diferencia a la foto original de la cara limpia.
# Se calcula el alfa mínimo y el color con los que "cara limpia + capa" reproduce la foto original.
cv2.imwrite(os.path.join(RES, "head.jpg"), sharpen(clean).round().astype(np.uint8), [cv2.IMWRITE_JPEG_QUALITY, 95])
cl = cv2.imread(os.path.join(RES, "head.jpg")).astype(np.float32)     # lo que realmente va a decodificar la app
o = sharpen(head)
# el halo claro que deja la nitidez junto al micrófono no es parte de la cara: se ignora
o = np.where(dist[:, :, None] <= 8.0, np.minimum(o, np.maximum(cl, head.astype(np.float32))), o)
DARK = 10.0
need = np.where(o < cl, (cl - o) / np.maximum(cl - DARK, 1.0), 0.0)                    # solo oscurece
alpha = np.clip(need.max(axis=2), 0, 1)
alpha[dist > 8.0] = 0
alpha[alpha < 0.06] = 0
alpha = np.maximum(alpha, cv2.erode(keep, np.ones((3, 3), np.uint8)).astype(np.float32))   # el cuerpo del micrófono es opaco
a3 = np.maximum(alpha, 1e-3)[:, :, None]
mic_rgb = np.clip((o - cl * (1 - a3)) / a3, 0, 255)
mic = np.dstack([mic_rgb, alpha * 255]).round().astype(np.uint8)
mic[mic[:, :, 3] == 0] = 0
cv2.imwrite(os.path.join(RES, "head_mic.png"), mic, [cv2.IMWRITE_PNG_COMPRESSION, 9])

# control: cara limpia + micrófono encima debe verse igual que la foto original
cl = cv2.imread(os.path.join(RES, "head.jpg")).astype(np.float32)
mk = cv2.imread(os.path.join(RES, "head_mic.png"), cv2.IMREAD_UNCHANGED).astype(np.float32)
am = mk[:, :, 3:4] / 255
rest = cl * (1 - am) + mk[:, :, :3] * am
orig = sharpen(head)
dif = np.abs(rest - orig).max(axis=2)
print("reposo vs original: dif media %.2f, p99 %.1f, max %.0f" % (dif.mean(), np.percentile(dif, 99), dif.max()))
if CHECK:
    os.makedirs(CHECK, exist_ok=True)
    cv2.imwrite(os.path.join(CHECK, "_rest_preview.png"), np.clip(rest, 0, 255).astype(np.uint8))
    cv2.imwrite(os.path.join(CHECK, "_orig_preview.png"), np.clip(orig, 0, 255).astype(np.uint8))
    cv2.imwrite(os.path.join(CHECK, "_clean_preview.png"), clean)
for n_ in ("avatar_full.jpg", "head.jpg", "head_mic.png"): print(n_, os.path.getsize(os.path.join(RES, n_)))
print("mask px:", int(m1.sum()), "head tex:", head.shape)
