package com.example.library.web;

import org.springframework.web.util.UriComponentsBuilder;
import java.util.Set;

/** Only list filters can be returned; the destination is fixed by the controller. */
public record ManagementReturn(String listQuery, String listStatus, String listPage) {
    public String target(String path, Set<String> statuses) {
        try {
            ManagementSearch search = ManagementSearch.parse(listQuery, listStatus, listPage, statuses);
            var uri = UriComponentsBuilder.fromPath(path);
            if (!search.query().isEmpty()) uri.queryParam("q", search.query());
            if (!search.status().equals("ALL")) uri.queryParam("status", search.status());
            if (search.page() != 0) uri.queryParam("page", search.page());
            return uri.build().encode().toUriString();
        } catch (IllegalArgumentException exception) {
            return path;
        }
    }
}
