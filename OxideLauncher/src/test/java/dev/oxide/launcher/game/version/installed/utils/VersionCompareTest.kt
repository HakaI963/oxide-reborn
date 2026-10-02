/*
 * Oxide Reborn
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package dev.oxide.launcher.game.version.installed.utils

import org.jackhuang.hmcl.util.versioning.GameVersionNumber
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ordering rules implemented by the vendored HMCL `GameVersionNumber`:
 *
 * * releases compare by major, minor, patch, then by release type in the enum order
 *   `UNKNOWN < SNAPSHOT < PRE_RELEASE < RELEASE_CANDIDATE < GA`, then by the EA version;
 * * legacy snapshots (`<yy>w<ww><letter>`) compare by their packed `yyww<x>` integer;
 * * comparing a release with a legacy snapshot needs the shipped release history, so
 *   `assets/game/versions.txt` must be reachable while the class initialiser runs;
 * * a version string that cannot be parsed sorts after every known version, so an unknown
 *   string never wins a comparison against a real version.
 */
class VersionCompareTest {

    @Test
    fun shippedVersionHistoryIsLoaded() {
        // Guards the classpath contract described above: without versions.txt the snapshot
        // tables stay empty and every snapshot-versus-release comparison becomes meaningless.
        assertTrue(
            "assets/game/versions.txt was not loaded, legacy snapshot ordering is degraded",
            GameVersionNumber.getDefaultGameVersions().isNotEmpty()
        )
    }

    @Test
    fun testCompareLegacyReleases() {
        assertTrue("1.8".isBiggerVer("1.7"))
        assertTrue("1.0".isBiggerOrEqualVer("1.0"))
        assertTrue("1.16".isLowerVer("1.17"))
    }

    @Test
    fun testCompareLegacySnapshots() {
        assertTrue("20w14b".isBiggerVer("20w14a"))
        assertTrue("20w14a".isLowerVer("23w40a"))
        assertTrue("20w30a".isBiggerVer("20w27a"))
    }

    @Test
    fun testCompareNewSnapshots() {
        // A snapshot of a version always precedes the release itself.
        assertTrue("25.4-snapshot-2".isLowerVer("25.4"))
        assertTrue("25.4-snapshot-2".isBiggerVer("25.4-snapshot-1"))
        assertTrue("26.2-snapshot-1".isLowerVer("26.2"))
    }

    @Test
    fun testCompareNewReleases() {
        assertTrue("26.3".isBiggerOrEqualVer("26.2"))
        assertTrue("26.1".isLowerOrEqualVer("26.1"))
        assertTrue("1.21.11".isLowerVer("26.1"))
    }

    @Test
    fun testNewVsLegacy() {
        // Both sides are releases here, so this only needs the numeric prefix comparison.
        assertTrue("26.1".isBiggerVer("1.21.11"))
        assertTrue("26.2-snapshot-1".isBiggerVer("1.21.11"))
        assertTrue("25.4-snapshot-1".isBiggerVer("23w40a"))
        // This one needs the shipped history: 20w14a shipped before 1.21.11.
        assertTrue("1.21.11".isBiggerVer("20w14a"))
    }

    @Test
    fun testVersionEquality() {
        assertTrue("1.21.5".isBiggerOrEqualVer("1.21.5"))
        assertTrue("1.21.5".isLowerOrEqualVer("1.21.5"))
        assertTrue("20w30a".isLowerOrEqualVer("1.21.5"))
    }

    @Test
    fun testUnknownVersionSortsLast() {
        // An unparsable version string must never be considered newer than a real version,
        // otherwise a corrupt or third-party version name could win a comparison.
        assertTrue("20w14b".isLowerVer("someRelease"))
        assertTrue("1.21.5".isLowerVer("someRelease"))
        assertTrue("someRelease".isBiggerVer("1.21.5"))
        assertTrue("someRelease".isBiggerVer("20w14b"))
    }
}