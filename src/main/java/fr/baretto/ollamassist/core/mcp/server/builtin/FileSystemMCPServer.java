package fr.baretto.ollamassist.core.mcp.server.builtin;

import fr.baretto.ollamassist.core.mcp.protocol.MCPResponse;
import fr.baretto.ollamassist.core.mcp.server.MCPServerConfig;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Serveur MCP intégré pour les opérations sur le système de fichiers
 */
@Slf4j
public class FileSystemMCPServer implements BuiltinMCPServer {

    private static final String SERVER_ID = "filesystem";
    private static final String SERVER_NAME = "File System";
    private static final String SERVER_VERSION = "1.0.0";

    @Override
    public String getId() {
        return SERVER_ID;
    }

    @Override
    public String getName() {
        return SERVER_NAME;
    }

    @Override
    public String getVersion() {
        return SERVER_VERSION;
    }

    @Override
    public MCPServerConfig getConfig() {
        return MCPServerConfig.builder()
                .id(SERVER_ID)
                .name(SERVER_NAME)
                .type(MCPServerConfig.MCPServerType.BUILTIN)
                .capabilities(List.of(
                        "fs/read_file",
                        "fs/write_file",
                        "fs/list_directory",
                        "fs/create_directory",
                        "fs/delete_file",
                        "fs/copy_file",
                        "fs/move_file",
                        "fs/file_exists",
                        "fs/get_file_info"
                ))
                .enabled(true)
                .build();
    }

    @Override
    public MCPResponse executeCapability(String capability, Map<String, Object> params) {
        log.debug("Executing FileSystem capability: {} with params: {}", capability, params);

        try {
            return switch (capability) {
                case "fs/read_file" -> readFile(params);
                case "fs/write_file" -> writeFile(params);
                case "fs/list_directory" -> listDirectory(params);
                case "fs/create_directory" -> createDirectory(params);
                case "fs/delete_file" -> deleteFile(params);
                case "fs/copy_file" -> copyFile(params);
                case "fs/move_file" -> moveFile(params);
                case "fs/file_exists" -> fileExists(params);
                case "fs/get_file_info" -> getFileInfo(params);
                default -> MCPResponse.error("Capability not supported: " + capability);
            };
        } catch (Exception e) {
            log.error("Error executing filesystem capability: {}", capability, e);
            return MCPResponse.error("Execution error: " + e.getMessage());
        }
    }

    private MCPResponse readFile(Map<String, Object> params) {
        String filePath = (String) params.get("path");
        if (filePath == null) {
            return MCPResponse.error("Parameter 'path' is required");
        }

        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return MCPResponse.error("File does not exist: " + filePath);
            }

            String content = Files.readString(path);
            return MCPResponse.success(Map.of(
                    "content", content,
                    "path", filePath,
                    "size", Files.size(path)
            ));
        } catch (IOException e) {
            return MCPResponse.error("Error reading file: " + e.getMessage());
        }
    }

    private MCPResponse writeFile(Map<String, Object> params) {
        String filePath = (String) params.get("path");
        String content = (String) params.get("content");

        if (filePath == null || content == null) {
            return MCPResponse.error("Parameters 'path' and 'content' are required");
        }

        try {
            Path path = Paths.get(filePath);
            // Créer les répertoires parents si nécessaire
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            Files.writeString(path, content);
            return MCPResponse.success(Map.of(
                    "path", filePath,
                    "size", Files.size(path),
                    "created", !Files.exists(path)
            ));
        } catch (IOException e) {
            return MCPResponse.error("Error writing file: " + e.getMessage());
        }
    }

    private MCPResponse listDirectory(Map<String, Object> params) {
        String dirPath = (String) params.get("path");
        if (dirPath == null) {
            return MCPResponse.error("Parameter 'path' is required");
        }

        try {
            Path path = Paths.get(dirPath);
            if (!Files.exists(path)) {
                return MCPResponse.error("Directory does not exist: " + dirPath);
            }

            if (!Files.isDirectory(path)) {
                return MCPResponse.error("Path is not a directory: " + dirPath);
            }

            List<Map<String, Object>> entries = Files.list(path)
                    .map(entry -> Map.<String, Object>of(
                            "name", entry.getFileName().toString(),
                            "path", entry.toString(),
                            "type", Files.isDirectory(entry) ? "directory" : "file",
                            "size", getFileSize(entry)
                    ))
                    .toList();

            return MCPResponse.success(Map.of(
                    "path", dirPath,
                    "entries", entries,
                    "count", entries.size()
            ));
        } catch (IOException e) {
            return MCPResponse.error("Error listing directory: " + e.getMessage());
        }
    }

    private MCPResponse createDirectory(Map<String, Object> params) {
        String dirPath = (String) params.get("path");
        if (dirPath == null) {
            return MCPResponse.error("Parameter 'path' is required");
        }

        try {
            Path path = Paths.get(dirPath);
            boolean created = !Files.exists(path);
            Files.createDirectories(path);

            return MCPResponse.success(Map.of(
                    "path", dirPath,
                    "created", created
            ));
        } catch (IOException e) {
            return MCPResponse.error("Error creating directory: " + e.getMessage());
        }
    }

    private MCPResponse deleteFile(Map<String, Object> params) {
        String filePath = (String) params.get("path");
        if (filePath == null) {
            return MCPResponse.error("Parameter 'path' is required");
        }

        try {
            Path path = Paths.get(filePath);
            boolean existed = Files.exists(path);
            boolean deleted = Files.deleteIfExists(path);

            return MCPResponse.success(Map.of(
                    "path", filePath,
                    "existed", existed,
                    "deleted", deleted
            ));
        } catch (IOException e) {
            return MCPResponse.error("Error deleting file: " + e.getMessage());
        }
    }

    private MCPResponse copyFile(Map<String, Object> params) {
        String sourcePath = (String) params.get("source");
        String targetPath = (String) params.get("target");

        if (sourcePath == null || targetPath == null) {
            return MCPResponse.error("Parameters 'source' and 'target' are required");
        }

        try {
            Path source = Paths.get(sourcePath);
            Path target = Paths.get(targetPath);

            if (!Files.exists(source)) {
                return MCPResponse.error("Source file does not exist: " + sourcePath);
            }

            // Créer les répertoires parents si nécessaire
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }

            Files.copy(source, target);
            return MCPResponse.success(Map.of(
                    "source", sourcePath,
                    "target", targetPath,
                    "size", Files.size(target)
            ));
        } catch (IOException e) {
            return MCPResponse.error("Error copying file: " + e.getMessage());
        }
    }

    private MCPResponse moveFile(Map<String, Object> params) {
        String sourcePath = (String) params.get("source");
        String targetPath = (String) params.get("target");

        if (sourcePath == null || targetPath == null) {
            return MCPResponse.error("Parameters 'source' and 'target' are required");
        }

        try {
            Path source = Paths.get(sourcePath);
            Path target = Paths.get(targetPath);

            if (!Files.exists(source)) {
                return MCPResponse.error("Source file does not exist: " + sourcePath);
            }

            // Créer les répertoires parents si nécessaire
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }

            Files.move(source, target);
            return MCPResponse.success(Map.of(
                    "source", sourcePath,
                    "target", targetPath
            ));
        } catch (IOException e) {
            return MCPResponse.error("Error moving file: " + e.getMessage());
        }
    }

    private MCPResponse fileExists(Map<String, Object> params) {
        String filePath = (String) params.get("path");
        if (filePath == null) {
            return MCPResponse.error("Parameter 'path' is required");
        }

        Path path = Paths.get(filePath);
        return MCPResponse.success(Map.of(
                "path", filePath,
                "exists", Files.exists(path),
                "type", Files.isDirectory(path) ? "directory" : (Files.isRegularFile(path) ? "file" : "other")
        ));
    }

    private MCPResponse getFileInfo(Map<String, Object> params) {
        String filePath = (String) params.get("path");
        if (filePath == null) {
            return MCPResponse.error("Parameter 'path' is required");
        }

        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return MCPResponse.error("File does not exist: " + filePath);
            }

            return MCPResponse.success(Map.of(
                    "path", filePath,
                    "exists", true,
                    "type", Files.isDirectory(path) ? "directory" : "file",
                    "size", getFileSize(path),
                    "readable", Files.isReadable(path),
                    "writable", Files.isWritable(path),
                    "lastModified", Files.getLastModifiedTime(path).toString()
            ));
        } catch (IOException e) {
            return MCPResponse.error("Error getting file info: " + e.getMessage());
        }
    }

    private long getFileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return -1;
        }
    }
}