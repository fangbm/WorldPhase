package dev.fangbm.worldphase.worldgen.noise;

/** Stable channel and coordinate hashing; intentional long overflow is modulo 2^64. */
final class NoiseHash {
    private static final long X_SALT = 0x9e3779b97f4a7c15L;
    private static final long Z_SALT = 0xd1b54a32d192ed03L;

    private NoiseHash() {}

    static long cell(long seed, long x, long z, long channel) {
        return mix(seed ^ channel ^ mix(x ^ X_SALT) ^ Long.rotateLeft(mix(z ^ Z_SALT), 32));
    }

    static long channel(long seed, long salt) {
        return mix(seed ^ salt);
    }

    static double unit(long hash) {
        return (hash >>> 11) * 0x1.0p-53;
    }

    /*
     * Stateless finalizer adapted from Sebastiano Vigna's SplitMix64 (2015):
     * https://prng.di.unimi.it/splitmix64.c
     *
     * Written in 2015 by Sebastiano Vigna (vigna@acm.org).
     * To the extent possible under law, the author has dedicated all copyright
     * and related and neighboring rights to this software to the public domain worldwide.
     * Permission to use, copy, modify, and/or distribute this software for any
     * purpose with or without fee is hereby granted.
     * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
     * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
     * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
     * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
     * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
     * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR
     * IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
     *
     * No generator state or sequence is used here.
     */
    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
