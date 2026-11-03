package com.itmecasey.aeroflux.profile;

import com.itmecasey.aeroflux.core.profile.ProfileBudget;
import com.itmecasey.aeroflux.core.profile.ProfileFormat;
import com.itmecasey.aeroflux.core.profile.ProfileReport;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/** Turns a {@link ProfileReport} into the lines the command prints. The arithmetic is in shared-core; this only lays it out. */
public final class ProfileReporter {
    /** How many of the heaviest single blocks the report lists. */
    public static final int HEAVIEST_LISTED = 5;
    /** How many kinds of block get their own line; the rest are in the group and total lines. */
    private static final int TYPES_LISTED = 12;
    private static final String KEY = "commands.aeroflux.profile.";

    private ProfileReporter() {
    }

    public static List<Component> lines(ProfileReport report) {
        int window = report.windowTicks();
        long totalNanos = report.total().nanos();
        List<Component> lines = new ArrayList<>();
        if (report.total().calls() == 0) {
            lines.add(Component.translatable(KEY + "empty", window, ProfileFormat.seconds(window)));
            return lines;
        }
        double totalMs = report.total().msPerTick(window);
        double allowed = ProfileBudget.allowedMs(report);
        lines.add(Component.translatable(KEY + "header", window, ProfileFormat.seconds(window),
                ProfileFormat.ms(totalMs), report.total().blocks()));
        lines.add(Component.translatable(KEY + "budget", ProfileFormat.ms(allowed),
                ProfileFormat.percent(ProfileBudget.fractionUsed(totalMs, allowed)),
                Component.translatable(KEY + (ProfileBudget.within(totalMs, allowed) ? "within" : "over"))));
        for (ProfileReport.Line group : report.groups()) {
            lines.add(Component.translatable(KEY + "group", group.type(), ProfileFormat.ms(group.msPerTick(window)),
                    ProfileFormat.percent(group.shareOf(totalNanos)), group.blocks()));
        }
        for (ProfileReport.Line type : report.types().subList(0, Math.min(TYPES_LISTED, report.types().size()))) {
            lines.add(Component.translatable(KEY + "type", type.type(), ProfileFormat.ms(type.msPerTick(window)),
                    ProfileFormat.percent(type.shareOf(totalNanos)), type.blocks(), ProfileFormat.micros(type.worstNanos())));
        }
        if (!report.heaviest().isEmpty()) {
            lines.add(Component.translatable(KEY + "heaviest"));
            for (ProfileReport.SiteLine site : report.heaviest()) {
                lines.add(Component.translatable(KEY + "block", site.type(), site.x(), site.y(), site.z(),
                        site.dimension(), ProfileFormat.ms(site.msPerTick(window))));
            }
        }
        return lines;
    }
}
