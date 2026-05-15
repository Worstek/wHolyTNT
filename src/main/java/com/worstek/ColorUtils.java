package com.worstek;

import net.md_5.bungee.api.*;
import java.util.regex.*;

public class ColorUtils
{
    private static final Pattern HEX_PATTERN;

    public static String translate(final String text) {
        final Matcher matcher = ColorUtils.HEX_PATTERN.matcher(text);
        final StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            final String hexCode = matcher.group();
            final ChatColor color = ChatColor.of(hexCode);
            matcher.appendReplacement(buffer, color.toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    static {
        HEX_PATTERN = Pattern.compile("#[a-fA-F0-9]{6}");
    }
}
