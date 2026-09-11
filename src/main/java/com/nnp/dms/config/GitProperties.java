/**
 * GitProperties.java
 *
 * @author Soumojit Makar
 * @date 29-Jul-2026
 */
package com.nnp.dms.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
// Maps the `git.repository.*` config properties (url/branch/username/token) to a typed bean.
@ConfigurationProperties(prefix = "git.repository")
public class GitProperties {
    private String url;       // HTTPS URL of the DMS deploy-script repo (GitLab)
    private String branch;    // Branch checked out when cloning
    private String username;  // Git authentication username
    private String token;     // Git authentication token (embedded in the clone URL)
    public String getUrl() {
        return url;
    }
    public void setUrl(String url) {
        this.url = url;
    }
    public String getBranch() {
        return branch;
    }
    public void setBranch(String branch) {
        this.branch = branch;
    }
    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }
    public String getToken() {
        return token;
    }
    public void setToken(String token) {
        this.token = token;
    }

}
