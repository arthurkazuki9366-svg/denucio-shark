package br.com.sharksmp.denuncias;

import java.util.UUID;

/** Uma denúncia registrada. */
public final class Report {
    public final int id;
    public final UUID reporterId;
    public final String reporterName;
    public final UUID targetId;
    public final String targetName;
    public final String reason;
    public final long createdAt;
    public boolean resolved;
    public String resolvedBy;
    public long resolvedAt;

    public Report(int id, UUID reporterId, String reporterName, UUID targetId, String targetName,
                  String reason, long createdAt) {
        this.id = id;
        this.reporterId = reporterId;
        this.reporterName = reporterName;
        this.targetId = targetId;
        this.targetName = targetName;
        this.reason = reason;
        this.createdAt = createdAt;
    }
}
