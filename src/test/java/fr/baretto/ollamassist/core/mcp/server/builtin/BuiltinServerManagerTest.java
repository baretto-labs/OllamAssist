package fr.baretto.ollamassist.core.mcp.server.builtin;

import fr.baretto.ollamassist.core.mcp.protocol.MCPResponse;
import fr.baretto.ollamassist.core.mcp.server.MCPServerConfig;
import fr.baretto.ollamassist.core.mcp.server.MCPServerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@DisplayName("BuiltinServerManager Tests")
class BuiltinServerManagerTest {

    @Mock
    private MCPServerRegistry serverRegistry;

    private TestBuiltinServerManager manager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        manager = new TestBuiltinServerManager(serverRegistry);
    }

    @Test
    @DisplayName("Should initialize with builtin servers")
    void shouldInitializeWithBuiltinServers() {
        // When
        List<BuiltinMCPServer> servers = manager.getAllServers();

        // Then
        assertThat(servers).hasSize(2);
        assertThat(servers.stream().map(BuiltinMCPServer::getId))
                .containsExactlyInAnyOrder("filesystem", "websearch");
    }

    @Test
    @DisplayName("Should register servers in registry")
    void shouldRegisterServersInRegistry() {
        // Then - verify servers were registered (2 builtin servers: filesystem + websearch)
        verify(serverRegistry, times(2)).registerServer(any(MCPServerConfig.class));
    }

    @Test
    @DisplayName("Should execute capability on builtin server")
    void shouldExecuteCapabilityOnBuiltinServer() {
        // When
        MCPResponse response = manager.executeCapability("filesystem", "fs/file_exists", Map.of(
                "path", "/tmp/test.txt"
        ));

        // Then
        assertThat(response).isNotNull();
        // Note: Le résultat exact dépend de l'implémentation du FileSystemMCPServer
    }

    @Test
    @DisplayName("Should handle server not found")
    void shouldHandleServerNotFound() {
        // When
        MCPResponse response = manager.executeCapability("nonexistent", "some/capability", Map.of());

        // Then
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError().getMessage()).contains("Builtin server not found");
    }

    @Test
    @DisplayName("Should get server by ID")
    void shouldGetServerById() {
        // When
        var server = manager.getServer("filesystem");

        // Then
        assertThat(server).isPresent();
        assertThat(server.get().getId()).isEqualTo("filesystem");
        assertThat(server.get().getName()).isEqualTo("File System");
    }

    @Test
    @DisplayName("Should check capability support")
    void shouldCheckCapabilitySupport() {
        // When & Then
        assertThat(manager.hasCapability("filesystem", "fs/read_file")).isTrue();
        assertThat(manager.hasCapability("filesystem", "unknown/capability")).isFalse();
        assertThat(manager.hasCapability("nonexistent", "fs/read_file")).isFalse();
    }

    @Test
    @DisplayName("Should provide server configurations")
    void shouldProvideServerConfigurations() {
        // When
        List<MCPServerConfig> configs = manager.getAllServerConfigs();

        // Then
        assertThat(configs).hasSize(2);
        assertThat(configs.stream().map(MCPServerConfig::getId))
                .containsExactlyInAnyOrder("filesystem", "websearch");
        assertThat(configs.stream().allMatch(config ->
                config.getType() == MCPServerConfig.MCPServerType.BUILTIN)).isTrue();
    }

    @Test
    @DisplayName("Should provide statistics")
    void shouldProvideStatistics() {
        // When
        Map<String, String> stats = manager.getStats();

        // Then
        assertThat(stats.get("totalServers")).isEqualTo("2");
        assertThat(stats.get("activeServers")).isEqualTo("2");
        assertThat(stats.get("totalCapabilities")).isNotNull();
    }

    @Test
    @DisplayName("Should shutdown servers gracefully")
    void shouldShutdownServersGracefully() {
        // When
        manager.shutdown();

        // Then
        assertThat(manager.getAllServers()).isEmpty();
    }

    /**
     * Version de test du BuiltinServerManager qui n'utilise pas ApplicationManager
     */
    private static class TestBuiltinServerManager {

        private final MCPServerRegistry serverRegistry;
        private final java.util.Map<String, BuiltinMCPServer> builtinServers = new java.util.HashMap<>();

        public TestBuiltinServerManager(MCPServerRegistry serverRegistry) {
            this.serverRegistry = serverRegistry;
            initializeTestServers();
        }

        private void initializeTestServers() {
            registerBuiltinServer(new FileSystemMCPServer());
            registerBuiltinServer(new WebSearchMCPServer());
        }

        private void registerBuiltinServer(BuiltinMCPServer server) {
            try {
                server.start();
                builtinServers.put(server.getId(), server);
                serverRegistry.registerServer(server.getConfig());
            } catch (Exception e) {
                // Log error in real implementation
            }
        }

        public MCPResponse executeCapability(String serverId, String capability, Map<String, Object> params) {
            BuiltinMCPServer server = builtinServers.get(serverId);
            if (server == null) {
                return MCPResponse.error("Builtin server not found: " + serverId);
            }
            return server.executeCapability(capability, params);
        }

        public java.util.Optional<BuiltinMCPServer> getServer(String serverId) {
            return java.util.Optional.ofNullable(builtinServers.get(serverId));
        }

        public List<BuiltinMCPServer> getAllServers() {
            return List.copyOf(builtinServers.values());
        }

        public List<MCPServerConfig> getAllServerConfigs() {
            return builtinServers.values().stream()
                    .map(BuiltinMCPServer::getConfig)
                    .toList();
        }

        public boolean hasCapability(String serverId, String capability) {
            BuiltinMCPServer server = builtinServers.get(serverId);
            if (server == null) {
                return false;
            }
            return server.getConfig().getCapabilities().contains(capability);
        }

        public Map<String, String> getStats() {
            long activeServers = builtinServers.values().stream()
                    .mapToLong(server -> server.isAvailable() ? 1 : 0)
                    .sum();

            long totalCapabilities = builtinServers.values().stream()
                    .mapToLong(server -> server.getConfig().getCapabilities().size())
                    .sum();

            return Map.of(
                    "totalServers", String.valueOf(builtinServers.size()),
                    "activeServers", String.valueOf(activeServers),
                    "totalCapabilities", String.valueOf(totalCapabilities)
            );
        }

        public void shutdown() {
            builtinServers.values().forEach(BuiltinMCPServer::stop);
            builtinServers.clear();
        }
    }
}