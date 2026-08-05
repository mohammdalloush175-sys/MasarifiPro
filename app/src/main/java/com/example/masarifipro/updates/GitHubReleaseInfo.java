package com.example.masarifipro.updates;

public final class GitHubReleaseInfo {
    private final String tagName;
    private final String versionName;
    private final String title;
    private final String body;
    private final String releasePageUrl;
    private final String apkDownloadUrl;
    private final String apkFileName;
    private final String publishedAt;

    public GitHubReleaseInfo(
            String tagName,
            String versionName,
            String title,
            String body,
            String releasePageUrl,
            String apkDownloadUrl,
            String apkFileName,
            String publishedAt
    ) {
        this.tagName = tagName;
        this.versionName = versionName;
        this.title = title;
        this.body = body;
        this.releasePageUrl = releasePageUrl;
        this.apkDownloadUrl = apkDownloadUrl;
        this.apkFileName = apkFileName;
        this.publishedAt = publishedAt;
    }

    public String getTagName() {
        return tagName;
    }

    public String getVersionName() {
        return versionName;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getReleasePageUrl() {
        return releasePageUrl;
    }

    public String getApkDownloadUrl() {
        return apkDownloadUrl;
    }

    public String getApkFileName() {
        return apkFileName;
    }

    public String getPublishedAt() {
        return publishedAt;
    }
}
