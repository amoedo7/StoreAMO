package com.desarrollamo.storeamo.bootstrap;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ReleaseVersion implements Comparable<ReleaseVersion> {
    private static final Pattern TAG = Pattern.compile("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:\\.(\\d+))?$");

    final int major;
    final int minor;
    final int patch;
    final int build;
    final String text;

    private ReleaseVersion(int major, int minor, int patch, int build, String text) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.build = build;
        this.text = text;
    }

    static ReleaseVersion parseTag(String raw) {
        if (raw == null) return null;
        Matcher matcher = TAG.matcher(raw.trim());
        if (!matcher.matches()) return null;
        try {
            int major = Integer.parseInt(matcher.group(1));
            int minor = Integer.parseInt(matcher.group(2));
            int patch = Integer.parseInt(matcher.group(3));
            int build = matcher.group(4) == null ? 0 : Integer.parseInt(matcher.group(4));
            return new ReleaseVersion(major, minor, patch, build,
                    major + "." + minor + "." + patch + (matcher.group(4) == null ? "" : "." + build));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    boolean isAtLeast(int wantedMajor, int wantedMinor, int wantedPatch) {
        return compareTo(new ReleaseVersion(wantedMajor, wantedMinor, wantedPatch, 0, "")) >= 0;
    }

    @Override
    public int compareTo(ReleaseVersion other) {
        int value = Integer.compare(major, other.major);
        if (value != 0) return value;
        value = Integer.compare(minor, other.minor);
        if (value != 0) return value;
        value = Integer.compare(patch, other.patch);
        if (value != 0) return value;
        return Integer.compare(build, other.build);
    }
}
