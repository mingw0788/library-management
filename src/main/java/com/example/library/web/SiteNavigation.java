package com.example.library.web;

public record SiteNavigation(boolean authenticated, String loginName, String role, String roleLabel,
                             String workspacePath, String workspaceLabel, boolean reservationsEnabled,
                             String currentPath) {
}
