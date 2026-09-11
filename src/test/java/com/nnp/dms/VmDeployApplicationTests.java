package com.nnp.dms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nnp.dms.config.GitProperties;
import com.nnp.dms.config.OpenApiConfig;
import com.nnp.dms.controller.DeployController;
import com.nnp.dms.dto.*;
import com.nnp.dms.entity.DeploymentContainer;
import com.nnp.dms.entity.DeploymentEntity;
import com.nnp.dms.entity.Environment;
import com.nnp.dms.exception.DeployException;
import com.nnp.dms.repository.EnvironmentRepository;
import com.nnp.dms.service.*;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive Unit and Mock Controller tests for PICC-PP-NNP-DMS-Management.
 */
@ExtendWith(MockitoExtension.class)
class VmDeployApplicationTests {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private DeploymentService deploymentService;

    @Mock
    private SshService sshService;

    @Mock
    private ContainerService containerService;

    @InjectMocks
    private DeployController deployController;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        mockMvc = MockMvcBuilders.standaloneSetup(deployController).build();
    }

    @Nested
    @DisplayName("DeployController Web API Tests")
    class ControllerTests {

        @Test
        @DisplayName("POST /check-status returns 200 and VM health response")
        void testCheckStatus() throws Exception {
            VmStatusCheckRequest request = new VmStatusCheckRequest();
            request.setHost("192.0.2.10");
            request.setPort(22);
            request.setUsername("admin");
            request.setPrivateKey("mock-key");

            VmStatusResponse response = new VmStatusResponse();
            response.setHost("192.0.2.10");
            response.setOverall("HEALTHY");

            when(sshService.checkVmStatus(any(VmStatusCheckRequest.class))).thenReturn(response);

            mockMvc.perform(post("/check-status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.host").value("192.0.2.10"))
                    .andExpect(jsonPath("$.overall").value("HEALTHY"));
        }

        @Test
        @DisplayName("POST /create-dms returns 202 Accepted with location header")
        void testCreateDms() throws Exception {
            DeployRequest request = new DeployRequest();
            request.setEnvId("ENV-001");
            request.setHost("192.0.2.10");
            request.setPort(22);
            request.setUsername("admin");
            request.setPassword("admin-pass");
            request.setPrivateKey("mock-private-key");
            request.setFilePath("/opt/dms");

            DeploymentService.CreateDeploymentResult result =
                    new DeploymentService.CreateDeploymentResult("DEP-12345", "PENDING");

            when(deploymentService.submitDeployment(any(DeployRequest.class))).thenReturn(result);

            mockMvc.perform(post("/create-dms")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted())
                    .andExpect(header().string("Location", "/api/dms/deployments/DEP-12345"))
                    .andExpect(jsonPath("$.id").value("DEP-12345"))
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }

        @Test
        @DisplayName("GET /deployments/{id} returns 200 when found")
        void testGetDeploymentFound() throws Exception {
            DeploymentEntity entity = new DeploymentEntity();
            entity.setId("DEP-12345");
            entity.setStatus("DEPLOYED");

            DeploymentStatusResponse resp = new DeploymentStatusResponse();
            resp.setId("DEP-12345");
            resp.setStatus("DEPLOYED");

            when(deploymentService.getDeployment("DEP-12345")).thenReturn(Optional.of(entity));
            when(deploymentService.toFullResponse(entity)).thenReturn(resp);

            mockMvc.perform(get("/deployments/DEP-12345"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("DEP-12345"))
                    .andExpect(jsonPath("$.status").value("DEPLOYED"));
        }

        @Test
        @DisplayName("GET /deployments/{id} returns 404 when not found")
        void testGetDeploymentNotFound() throws Exception {
            when(deploymentService.getDeployment("DEP-NON-EXIST")).thenReturn(Optional.empty());

            mockMvc.perform(get("/deployments/DEP-NON-EXIST"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("GET /deployments returns paginated list")
        void testListDeployments() {
            DeploymentEntity entity = new DeploymentEntity();
            entity.setId("DEP-001");
            entity.setStatus("DEPLOYED");

            DeploymentStatusResponse full = new DeploymentStatusResponse();
            full.setId("DEP-001");
            full.setStatus("DEPLOYED");

            when(deploymentService.listDeployments(anyInt(), anyInt(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(entity)));
            when(deploymentService.toFullResponse(any(DeploymentEntity.class))).thenReturn(full);

            var resp = deployController.listDeployments(0, 20, "dms", "DEPLOYED");
            assertThat(resp.getStatusCode().value()).isEqualTo(200);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().getContent()).hasSize(1);
            assertThat(resp.getBody().getContent().get(0).getId()).isEqualTo("DEP-001");
        }

        @Test
        @DisplayName("POST /deployments/{id}/ssh-key stores cached key")
        void testStoreSshKey() throws Exception {
            SshKeyRequest keyReq = new SshKeyRequest();
            keyReq.setPrivateKey("new-private-key");

            when(deploymentService.refreshSshKey(eq("DEP-001"), eq("new-private-key"), any())).thenReturn(true);

            mockMvc.perform(post("/deployments/DEP-001/ssh-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(keyReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("GET /deployments/{id}/containers returns container list")
        void testListContainers() throws Exception {
            DeploymentEntity entity = new DeploymentEntity();
            entity.setId("DEP-001");

            DeploymentContainer c1 = new DeploymentContainer("DEP-001", "dms-web", "dms-image:latest", "Up 2 hours", LocalDateTime.now(ZoneId.systemDefault()));

            when(deploymentService.getDeployment("DEP-001")).thenReturn(Optional.of(entity));
            when(containerService.getContainers("DEP-001")).thenReturn(List.of(c1));

            mockMvc.perform(get("/deployments/DEP-001/containers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].containerName").value("dms-web"));
        }
    }

    @Nested
    @DisplayName("SshKeyCacheService Tests")
    class SshKeyCacheTests {

        @Test
        @DisplayName("Put and get valid cached SSH key")
        void testCachePutAndGet() {
            SshKeyCacheService cacheService = new SshKeyCacheService();
            cacheService.put("DEP-999", "secret-private-key", "passphrase123");

            Optional<SshKeyCacheService.CachedKey> cached = cacheService.get("DEP-999");
            assertThat(cached).isPresent();
            assertThat(cached.get().privateKey()).isEqualTo("secret-private-key");
            assertThat(cached.get().passphrase()).isEqualTo("passphrase123");

            cacheService.remove("DEP-999");
            assertThat(cacheService.get("DEP-999")).isEmpty();
        }
    }

    @Nested
    @DisplayName("EnvironmentPlanService Tests")
    class EnvironmentPlanTests {

        @Mock
        private EnvironmentRepository environmentRepository;

        @Mock
        private EnvActivityLogService activityLogService;

        @InjectMocks
        private EnvironmentPlanService planService;

        @Test
        @DisplayName("resolveEnvironment throws DeployException when not found")
        void testResolveEnvironmentNotFound() {
            when(environmentRepository.findByEnvId("INVALID")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> planService.resolveEnvironment("INVALID"))
                    .isInstanceOf(DeployException.class)
                    .hasMessageContaining("Environment not found");
        }

        @Test
        @DisplayName("resolveEnvironment returns Environment entity when found")
        void testResolveEnvironmentSuccess() {
            Environment env = new Environment();
            env.setEnvId("ENV-1");
            env.setEnvCustId("CUST-1");

            when(environmentRepository.findByEnvId("ENV-1")).thenReturn(Optional.of(env));

            Environment resolved = planService.resolveEnvironment("ENV-1");
            assertThat(resolved.getEnvId()).isEqualTo("ENV-1");
            assertThat(resolved.getEnvCustId()).isEqualTo("CUST-1");
        }
    }

    @Nested
    @DisplayName("Configuration & Metadata Tests")
    class ConfigMetadataTests {

        @Test
        @DisplayName("OpenApiConfig produces valid OpenAPI bean with Apache 2.0 license")
        void testOpenApiConfig() {
            OpenApiConfig config = new OpenApiConfig();
            OpenAPI openAPI = config.customOpenAPI();

            assertThat(openAPI.getInfo().getTitle()).isEqualTo("PICC-PP-NNP-DMS-Management REST API");
            assertThat(openAPI.getInfo().getLicense().getName()).isEqualTo("Apache License 2.0");
            assertThat(openAPI.getInfo().getContact().getEmail()).isEqualTo("contribution@nubons.com");
        }

        @Test
        @DisplayName("GitProperties getters and setters work correctly")
        void testGitProperties() {
            GitProperties props = new GitProperties();
            props.setUrl("https://gitlab.example.com/repo.git");
            props.setBranch("develop");
            props.setUsername("ci-user");
            props.setToken("secret-token");

            assertThat(props.getUrl()).isEqualTo("https://gitlab.example.com/repo.git");
            assertThat(props.getBranch()).isEqualTo("develop");
            assertThat(props.getUsername()).isEqualTo("ci-user");
            assertThat(props.getToken()).isEqualTo("secret-token");
        }
    }
}
