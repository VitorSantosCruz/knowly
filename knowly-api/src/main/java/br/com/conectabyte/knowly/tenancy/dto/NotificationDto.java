package br.com.conectabyte.knowly.tenancy.dto;

import br.com.conectabyte.knowly.tenancy.Notification;
import br.com.conectabyte.knowly.tenancy.NotificationType;
import java.time.Instant;

public record NotificationDto(
        Long id,
        NotificationType type,
        Long tenantMembershipId,
        Long tenantId,
        String tenantName,
        boolean resolved,
        Instant createdAt) {

    public static NotificationDto from(Notification notification) {
        var membership = notification.getTenantMembership();

        if (membership == null) {
            return new NotificationDto(
                    notification.getId(),
                    notification.getType(),
                    null,
                    null,
                    null,
                    notification.isResolved(),
                    notification.getCreatedAt());
        }

        var tenant = membership.getTenant();

        return new NotificationDto(
                notification.getId(),
                notification.getType(),
                membership.getId(),
                tenant.getId(),
                tenant.getName(),
                notification.isResolved(),
                notification.getCreatedAt());
    }
}
