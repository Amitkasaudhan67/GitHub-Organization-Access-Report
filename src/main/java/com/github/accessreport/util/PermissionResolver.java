package com.github.accessreport.util;

import com.github.accessreport.dto.PermissionDto;
import com.github.accessreport.model.AccessPermission;

/** Converts GitHub's granular permission flags into the public report levels. */
public final class PermissionResolver {

    private PermissionResolver() {
    }

    public static AccessPermission resolve(PermissionDto permissions) {
        if (permissions != null && Boolean.TRUE.equals(permissions.admin())) {
            return AccessPermission.ADMIN;
        }
        if (permissions != null && (Boolean.TRUE.equals(permissions.push())
                || Boolean.TRUE.equals(permissions.maintain()))) {
            return AccessPermission.WRITE;
        }
        return AccessPermission.READ;
    }
}
