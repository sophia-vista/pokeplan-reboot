#!/usr/bin/env python3
import argparse
import hashlib
import struct
import subprocess
import sys
import wave
from pathlib import Path

import numpy as np

ROM_SHA1 = "9f4cfc5b5f4859d17169a485462e977c7aac2b89"
SONG_TABLE = 0x9E866BC
SONG_COUNT = 941
SR = 32000
FRAME_HZ = 59.7275
CLOCK = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24,
         28, 30, 32, 36, 40, 42, 44, 48, 52, 54, 56, 60, 64, 66, 68, 72, 76, 78, 80, 84, 88, 90, 92, 96]


class Rom:
    def __init__(self, data):
        self.d = data

    def off(self, ptr):
        return ptr & 0x1FFFFFF

    def u8(self, o):
        return self.d[o]

    def u16(self, o):
        return struct.unpack_from("<H", self.d, o)[0]

    def u32(self, o):
        return struct.unpack_from("<I", self.d, o)[0]

    def ptr(self, o):
        v = self.u32(o)
        return v if 0x08000000 <= v < 0x0A000000 else None


class Sample:
    def __init__(self, rom, ptr):
        o = rom.off(ptr)
        self.type, self.status = rom.u16(o), rom.u16(o + 2)
        self.rate = rom.u32(o + 4) / 1024.0
        self.loop_start, self.size = rom.u32(o + 8), rom.u32(o + 12)
        raw = rom.d[o + 16:o + 16 + (self.size if self.type == 0 else (self.size // 64 + 1) * 33)]
        if self.type == 0:
            pcm = np.frombuffer(raw, dtype=np.int8).astype(np.float32) / 128.0
        else:
            pcm = bdpcm(raw, self.size)
        self.pcm = pcm[:self.size] if len(pcm) >= self.size else pcm
        self.looped = bool(self.status & 0x4000)


def bdpcm(raw, size):
    delta = [0, 1, 4, 9, 16, 25, 36, 49, -64, -49, -36, -25, -16, -9, -4, -1]
    out, i = [], 0
    while len(out) < size and i + 33 <= len(raw):
        value = struct.unpack_from("b", raw, i)[0]
        out.append(value)
        flag = 0
        for k in range(1, 33):
            b = raw[i + k]
            for nib in (b >> 4, b & 15):
                if len(out) % 64 == 0:
                    break
                value = (value + delta[nib]) & 0xFF
                value = value - 256 if value > 127 else value
                out.append(value)
        i += 33
    return np.array(out, dtype=np.float32) / 128.0


class Voice:
    def __init__(self, rom, o):
        self.type, self.key, self.b2, self.b3 = rom.d[o:o + 4]
        self.word = rom.u32(o + 4)
        self.attack, self.decay, self.sustain, self.release = rom.d[o + 8:o + 12]
        self.word8 = rom.u32(o + 8)


class Bank:
    def __init__(self, rom, ptr, samples):
        self.rom, self.o, self.samples, self.cache = rom, rom.off(ptr), samples, {}

    def voice(self, n):
        if n not in self.cache:
            self.cache[n] = Voice(self.rom, self.o + 12 * n)
        return self.cache[n]

    def resolve(self, n, key):
        v = self.voice(n)
        if v.type == 0x40:
            sub = Bank(self.rom, v.word, self.samples)
            return sub.voice(self.rom.u8(self.rom.off(v.word8) + key)), key
        if v.type == 0x80:
            sub = Bank(self.rom, v.word, self.samples)
            d = sub.voice(key)
            return d, d.key
        return v, key

    def sample(self, v):
        if v.word not in self.samples:
            self.samples[v.word] = Sample(self.rom, v.word)
        return self.samples[v.word]


def envelope(v, n_on, n_total, cgb):
    frames_on = int(n_on * FRAME_HZ / SR) + 1
    if cgb:
        att = v.attack & 7
        dec = v.decay & 7
        sus = (v.sustain & 15) / 15.0
        rel = v.release & 7
        env, level, stage, out = [], 0.0, 0, []
        step_a = 1.0 if att == 0 else 1.0 / (att * 4)
        for _ in range(frames_on):
            if stage == 0:
                level = min(1.0, level + step_a)
                if level >= 1.0:
                    stage = 1
            elif stage == 1:
                level = max(sus, level - (1.0 if dec == 0 else 1.0 / (dec * 8)))
            out.append(level)
        rel_mul = 0.0 if rel == 0 else (1.0 - 1.0 / (rel * 6))
    else:
        level, stage, out = 0.0, 0, []
        sus = v.sustain / 255.0
        for _ in range(frames_on):
            if stage == 0:
                level = 1.0 if v.attack == 255 else min(1.0, level + v.attack / 255.0)
                if level >= 1.0:
                    stage = 1
            elif stage == 1:
                level = max(sus, level * v.decay / 256.0)
            out.append(level)
        rel_mul = v.release / 256.0
    frames = np.array(out, dtype=np.float32)
    env = np.repeat(frames, int(np.ceil(SR / FRAME_HZ)))[:n_on]
    if len(env) < n_on:
        env = np.pad(env, (0, n_on - len(env)), constant_values=frames[-1])
    tail = n_total - n_on
    if tail > 0 and rel_mul > 0:
        per = int(np.ceil(SR / FRAME_HZ))
        k = np.arange(tail) // per + 1
        t = env[-1] * (rel_mul ** k) if n_on else np.zeros(tail)
        t[t < 1.0 / 512] = 0
        env = np.concatenate([env, t.astype(np.float32)])
    elif tail > 0:
        env = np.concatenate([env, np.zeros(tail, dtype=np.float32)])
    return env[:n_total]


def tail_samples(v, cgb):
    r = (v.release & 7) if cgb else v.release
    if r == 0:
        return 0
    mul = (1.0 - 1.0 / (r * 6)) if cgb else r / 256.0
    if mul <= 0:
        return 0
    frames = int(np.ceil(np.log(1.0 / 512) / np.log(mul))) + 1
    return min(int(frames * SR / FRAME_HZ), SR * 3)


def play_sample(s, ratio, n):
    step = s.rate * ratio / SR
    pos = np.arange(n, dtype=np.float64) * step
    size = len(s.pcm)
    if size < 2:
        return np.zeros(n, dtype=np.float32)
    if s.looped and s.loop_start < size - 1:
        span = size - s.loop_start
        pos = np.where(pos >= size, s.loop_start + (pos - s.loop_start) % span, pos)
        alive = np.ones(n, dtype=bool)
    else:
        alive = pos < size - 1
    pos = np.minimum(pos, size - 2)
    i = pos.astype(np.int64)
    f = (pos - i).astype(np.float32)
    out = s.pcm[i] * (1 - f) + s.pcm[np.minimum(i + 1, size - 1)] * f
    return np.where(alive, out, 0).astype(np.float32)


def synth(v, key, n, bank, rom):
    base = v.type & 7
    freq = 440.0 * 2 ** ((key - 69) / 12.0)
    t = np.arange(n, dtype=np.float64)
    if base in (1, 2):
        duty = [0.125, 0.25, 0.5, 0.75][v.word & 3]
        return np.where(((t * freq / SR) % 1.0) < duty, 0.5, -0.5).astype(np.float32)
    if base == 3:
        o = rom.off(v.word)
        nib = np.array([x for b in rom.d[o:o + 16] for x in (b >> 4, b & 15)], dtype=np.float32) / 15.0 - 0.5
        return nib[((t * freq * 32 / SR) % 32).astype(np.int64)]
    rng = np.random.default_rng(int(key))
    period = max(1, int(SR / min(freq * 8, SR / 2)))
    steps = rng.random(n // period + 2) - 0.5
    return np.repeat(steps, period)[:n].astype(np.float32)


class Track:
    def __init__(self, ptr):
        self.pos = ptr
        self.wait = 0
        self.vol = 100
        self.voice = 0
        self.keysh = 0
        self.bend = 0
        self.bendr = 2
        self.tune = 0
        self.last_cmd = 0
        self.last_key = 60
        self.last_vel = 127
        self.stack = []
        self.reps = {}
        self.done = False
        self.active = []
        self.notes = []
        self.vols = [(0, 100)]


def render_song(rom, ptr, samples, max_seconds):
    o = rom.off(ptr)
    ntracks = rom.u8(o)
    bank = Bank(rom, rom.u32(o + 4), samples)
    tracks = [Track(rom.u32(o + 8 + 4 * i)) for i in range(ntracks)]
    tempo = 75
    now, tick = 0.0, 0
    max_ticks = int(max_seconds * FRAME_HZ * 150 / 150 * 1.0) + 1
    while not all(t.done for t in tracks) and tick < max_ticks * 4 and now < max_seconds * SR:
        for tr in tracks:
            keep = []
            for note in tr.active:
                if note["len"] is not None:
                    note["len"] -= 1
                    if note["len"] <= 0:
                        note["end"] = int(now)
                        tr.notes.append(note)
                        continue
                keep.append(note)
            tr.active = keep
            if tr.done:
                continue
            if tr.wait > 0:
                tr.wait -= 1
            guard = 0
            while tr.wait == 0 and not tr.done and guard < 2000:
                guard += 1
                cmd = rom.d[rom.off(tr.pos)]
                if cmd < 0x80:
                    cmd = tr.last_cmd
                    if cmd < 0x80:
                        tr.done = True
                        break
                else:
                    tr.pos += 1
                    if cmd >= 0xBD:
                        tr.last_cmd = cmd
                if cmd <= 0xB0:
                    tr.wait = CLOCK[cmd - 0x80]
                elif cmd == 0xB1:
                    tr.done = True
                elif cmd == 0xB2:
                    target = rom.u32(rom.off(tr.pos))
                    if target <= tr.pos:
                        tr.done = True
                    else:
                        tr.pos = target
                elif cmd == 0xB3:
                    target = rom.u32(rom.off(tr.pos))
                    tr.stack.append(tr.pos + 4)
                    tr.pos = target
                elif cmd == 0xB4:
                    if tr.stack:
                        tr.pos = tr.stack.pop()
                elif cmd == 0xB5:
                    count = rom.u8(rom.off(tr.pos))
                    target = rom.u32(rom.off(tr.pos + 1))
                    key = tr.pos
                    done_so_far = tr.reps.get(key, 0)
                    if done_so_far + 1 < count:
                        tr.reps[key] = done_so_far + 1
                        tr.pos = target
                    else:
                        tr.reps.pop(key, None)
                        tr.pos += 5
                elif cmd == 0xB9:
                    tr.pos += 3
                elif cmd in (0xBA, 0xC2, 0xC3, 0xC4, 0xC5, 0xC6, 0xC7):
                    tr.pos += 1
                elif cmd == 0xBB:
                    tempo = max(1, rom.u8(rom.off(tr.pos)))
                    tr.pos += 1
                elif cmd == 0xBC:
                    tr.keysh = struct.unpack_from("b", rom.d, rom.off(tr.pos))[0]
                    tr.pos += 1
                elif cmd == 0xBD:
                    tr.voice = rom.u8(rom.off(tr.pos))
                    tr.pos += 1
                elif cmd == 0xBE:
                    tr.vol = rom.u8(rom.off(tr.pos))
                    tr.vols.append((int(now), tr.vol))
                    tr.pos += 1
                elif cmd == 0xBF:
                    tr.pos += 1
                elif cmd == 0xC0:
                    tr.bend = rom.u8(rom.off(tr.pos)) - 64
                    tr.pos += 1
                elif cmd == 0xC1:
                    tr.bendr = rom.u8(rom.off(tr.pos))
                    tr.pos += 1
                elif cmd == 0xC8:
                    tr.tune = rom.u8(rom.off(tr.pos)) - 64
                    tr.pos += 1
                elif cmd == 0xCD:
                    tr.pos += 2
                elif cmd == 0xCE:
                    if rom.u8(rom.off(tr.pos)) < 0x80:
                        key = rom.u8(rom.off(tr.pos))
                        tr.pos += 1
                        match = [n for n in tr.active if n["len"] is None and n["key"] == key]
                    else:
                        match = [n for n in tr.active if n["len"] is None]
                    for n in match:
                        n["end"] = int(now)
                        tr.notes.append(n)
                        tr.active.remove(n)
                elif cmd >= 0xCF:
                    length = None if cmd == 0xCF else CLOCK[cmd - 0xCF]
                    tr.last_cmd = cmd
                    if rom.u8(rom.off(tr.pos)) < 0x80:
                        tr.last_key = rom.u8(rom.off(tr.pos))
                        tr.pos += 1
                        if rom.u8(rom.off(tr.pos)) < 0x80:
                            tr.last_vel = rom.u8(rom.off(tr.pos))
                            tr.pos += 1
                            if rom.u8(rom.off(tr.pos)) < 0x80 and length is not None:
                                length += rom.u8(rom.off(tr.pos))
                                tr.pos += 1
                            elif rom.u8(rom.off(tr.pos)) < 0x80:
                                tr.pos += 1
                    tr.active.append(dict(start=int(now), len=length, key=tr.last_key, vel=tr.last_vel,
                                          voice=tr.voice, bend=tr.bend, bendr=tr.bendr, tune=tr.tune,
                                          keysh=tr.keysh, vol=tr.vol, end=None))
                else:
                    tr.pos += 1
        now += SR / (FRAME_HZ * (2 * tempo) / 150.0)
        tick += 1
    end_all = int(now)
    mix = np.zeros(end_all + SR * 4, dtype=np.float32)
    for tr in tracks:
        for n in tr.active:
            n["end"] = end_all
            tr.notes.append(n)
        buf = np.zeros(len(mix), dtype=np.float32)
        for n in tr.notes:
            key = n["key"] + n["keysh"]
            v, pkey = bank.resolve(n["voice"], key)
            if v.type in (0x40, 0x80):
                continue
            cgb = (v.type & 7) in (1, 2, 3, 4)
            if not cgb and v.word == 0:
                continue
            n_on = max(1, n["end"] - n["start"])
            semis = (pkey + n["keysh"] if v.type in (0x00, 0x08, 0x10) else pkey) - 60
            semis += n["bend"] * n["bendr"] / 64.0 + n["tune"] / 64.0
            n_total = n_on + tail_samples(v, cgb)
            if cgb:
                wavef = synth(v, pkey + n["keysh"] + n["bend"] * n["bendr"] / 64.0, n_total, bank, rom)
            else:
                fixed = v.type == 0x08
                s = bank.sample(v)
                wavef = play_sample(s, 1.0 if fixed else 2 ** (semis / 12.0), n_total)
            sound = wavef * envelope(v, n_on, n_total, cgb) * (n["vel"] / 127.0)
            end = min(len(buf), n["start"] + len(sound))
            buf[n["start"]:end] += sound[:end - n["start"]]
        vols = np.empty(len(buf), dtype=np.float32)
        points = tr.vols + [(len(buf), tr.vols[-1][1])]
        for (a, va), (b, _) in zip(points, points[1:]):
            vols[a:b] = va / 127.0
        mix += buf * vols
    length = end_all
    trailing = np.nonzero(np.abs(mix) > 1.0 / 400)[0]
    if len(trailing):
        length = max(length, int(trailing[-1]) + 1)
    return mix[:length], end_all / SR, ntracks


def write_wav(path, audio, gain):
    pcm = np.clip(audio * gain, -1, 1)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((pcm * 32767).astype("<i2").tobytes())


def main():
    p = argparse.ArgumentParser()
    p.add_argument("rom")
    p.add_argument("out")
    p.add_argument("--songs", default=f"0-{SONG_COUNT - 1}")
    p.add_argument("--max-seconds", type=float, default=120)
    p.add_argument("--gain", type=float, default=0.5)
    p.add_argument("--ogg", action="store_true")
    args = p.parse_args()
    data = Path(args.rom).read_bytes()
    if hashlib.sha1(data).hexdigest() != ROM_SHA1:
        sys.exit("This is not the Red Rescue Team (USA) 1.0 ROM (SHA-1 mismatch).")
    rom = Rom(data)
    a, b = (int(x) for x in args.songs.split("-"))
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    samples, rows = {}, []
    for index in range(a, b + 1):
        o = rom.off(SONG_TABLE) + index * 8
        ptr, player = rom.u32(o), rom.u16(o + 4)
        if rom.off(ptr) + 8 > len(data) or rom.u8(rom.off(ptr)) == 0:
            continue
        try:
            audio, seconds, tracks = render_song(rom, ptr, samples, args.max_seconds)
        except (IndexError, struct.error, ValueError) as e:
            print("SKIP", index, repr(e), file=sys.stderr)
            continue
        peak = float(np.abs(audio).max()) if len(audio) else 0.0
        if peak < 1e-4:
            print("silent", index, file=sys.stderr)
            continue
        wav = out / f"{index:03d}.wav"
        write_wav(wav, audio, min(args.gain, 0.98 / peak))
        if args.ogg:
            subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "3",
                            str(wav.with_suffix(".ogg"))], check=True)
            wav.unlink()
        rows.append((index, player, f"{len(audio) / SR:.2f}", tracks, f"{peak:.3f}"))
        print(index, "player", player, f"{len(audio) / SR:.2f}s", flush=True)
    with open(out / "songs.tsv", "w", encoding="utf-8") as f:
        f.write("index\tplayer\tseconds\ttracks\traw_peak\n")
        for r in rows:
            f.write("\t".join(map(str, r)) + "\n")
    print(len(rows), "songs written")


if __name__ == "__main__":
    main()
