package com.cloudstorage.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cloudstorage.api.config.AuthCookies;
import com.cloudstorage.api.entity.User;
import com.cloudstorage.api.entity.UserRole;
import com.cloudstorage.api.repository.UserRepository;
import com.cloudstorage.api.service.LicenseValidatorService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Tests de integración de la API: arrancan la aplicación completa (filtros de seguridad,
 * controladores, servicios, Flyway y H2 en memoria) y la prueban con peticiones HTTP simuladas
 * mediante {@link MockMvc}.
 *
 * <p>La licencia se sustituye por un mock ({@code @MockBean}) para que los tests no dependan de la
 * fecha de caducidad de una licencia real. Cada test usa su propia IP para que el rate limit de
 * unos no afecte a otros.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {

    /** Cuota por usuario en los tests: 1 MB, para poder probar que se supera. */
    private static final long USER_QUOTA = 1024 * 1024;

    private static final String ADMIN_PASSWORD = "Admin-De-Prueba-123";
    private static final AtomicInteger IP_COUNTER = new AtomicInteger(1);

    @TempDir static Path tempDir;

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockBean private LicenseValidatorService licenseValidatorService;

    /** IP distinta por test: el rate limit cuenta por IP. */
    private String clientIp;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(
                envFile,
                "HOST_STORAGE_PATH=./uploads\nB2B_ADMIN_PASSWORD=" + ADMIN_PASSWORD + "\n");
        registry.add("app.storage.location", () -> tempDir.resolve("uploads").toString());
        registry.add("app.storage.quota-bytes", () -> USER_QUOTA);
        registry.add("app.env-file", envFile::toString);
    }

    @BeforeEach
    void setUp() {
        when(licenseValidatorService.getAllowedQuota()).thenReturn(50L * 1024 * 1024 * 1024);
        when(licenseValidatorService.isLicenseValid()).thenReturn(true);
        when(licenseValidatorService.getLicensedTo()).thenReturn("Tests");
        clientIp = "10.0.0." + IP_COUNTER.getAndIncrement();
    }

    // ------------------------------------------------------------------ utilidades

    private RequestPostProcessor fromClientIp() {
        return request -> {
            request.setRemoteAddr(clientIp);
            return request;
        };
    }

    private MockHttpServletRequestBuilder withJson(
            MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body).with(fromClientIp());
    }

    private MockHttpServletRequestBuilder auth(
            MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token).with(fromClientIp());
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private String register(String email) throws Exception {
        String body =
                mvc.perform(
                                withJson(
                                        post("/api/auth/register"),
                                        """
                                        {"name":"Test","email":"%s","password":"ClaveSegura123"}
                                        """
                                                .formatted(email)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    /**
     * Crea un administrador directamente en la BD (el orden de los tests no es fijo, así que no se
     * puede contar con ser "el primer usuario") e inicia sesión con él.
     */
    private String adminToken() throws Exception {
        String email = uniqueEmail();
        userRepository.save(
                User.builder()
                        .name("Admin")
                        .email(email)
                        .password(passwordEncoder.encode("ClaveSegura123"))
                        .role(UserRole.ROLE_ADMIN)
                        .build());
        String body =
                mvc.perform(
                                withJson(
                                        post("/api/auth/login"),
                                        "{\"email\":\"%s\",\"password\":\"ClaveSegura123\"}"
                                                .formatted(email)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    private JsonNode upload(String token, String name, byte[] content) throws Exception {
        String body =
                mvc.perform(
                                auth(
                                        multipart("/api/files/upload")
                                                .file(
                                                        new MockMultipartFile(
                                                                "file",
                                                                name,
                                                                "text/plain",
                                                                content)),
                                        token))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(body);
    }

    private String createFolder(String token, String name) throws Exception {
        String body =
                mvc.perform(
                                auth(
                                        withJson(
                                                post("/api/folders"),
                                                "{\"name\":\"" + name + "\"}"),
                                        token))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(body).get("id").asText();
    }

    // ------------------------------------------------------------------ seguridad

    @Nested
    @DisplayName("Seguridad")
    class Seguridad {

        @Test
        @DisplayName("Sin token, un endpoint protegido responde 401")
        void protectedEndpointWithoutTokenReturns401() throws Exception {
            mvc.perform(get("/api/files").with(fromClientIp()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Un token manipulado responde 401")
        void tamperedTokenReturns401() throws Exception {
            String token = register(uniqueEmail());
            String tampered = token.substring(0, token.length() - 2) + "xx";
            mvc.perform(auth(get("/api/files"), tampered)).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("/api/info es público")
        void infoIsPublic() throws Exception {
            mvc.perform(get("/api/info").with(fromClientIp())).andExpect(status().isOk());
        }

        @Test
        @DisplayName("Listar y crear enlaces compartidos exige sesión")
        void shareManagementRequiresAuth() throws Exception {
            mvc.perform(get("/api/share").with(fromClientIp()))
                    .andExpect(status().isUnauthorized());
            mvc.perform(withJson(post("/api/share/files/" + UUID.randomUUID()), "{}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Un usuario no puede descargar archivos de otro (404, sin revelar que existe)")
        void cannotAccessOtherUsersFiles() throws Exception {
            String owner = register(uniqueEmail());
            String intruder = register(uniqueEmail());
            String fileId = upload(owner, "privado.txt", "secreto".getBytes()).get("id").asText();

            mvc.perform(auth(get("/api/files/download/" + fileId), intruder))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Más de 15 intentos de login por minuto desde la misma IP responden 429")
        void loginIsRateLimited() throws Exception {
            String body = "{\"email\":\"nadie@example.com\",\"password\":\"incorrecta1\"}";
            for (int i = 0; i < 15; i++) {
                mvc.perform(withJson(post("/api/auth/login"), body))
                        .andExpect(status().isUnauthorized());
            }
            mvc.perform(withJson(post("/api/auth/login"), body))
                    .andExpect(status().isTooManyRequests());
        }

        @Test
        @DisplayName("X-Forwarded-For no permite saltarse el rate limit")
        void forwardedHeaderDoesNotBypassRateLimit() throws Exception {
            String body = "{\"email\":\"nadie@example.com\",\"password\":\"incorrecta1\"}";
            for (int i = 0; i < 15; i++) {
                mvc.perform(withJson(post("/api/auth/login"), body));
            }
            mvc.perform(
                            withJson(post("/api/auth/login"), body)
                                    .header("X-Forwarded-For", "1.2.3.4"))
                    .andExpect(status().isTooManyRequests());
        }
    }

    // ------------------------------------------------------------------ autenticación

    @Nested
    @DisplayName("Autenticación")
    class Autenticacion {

        @Test
        @DisplayName("Registro con email repetido (en otras mayúsculas) responde 409")
        void duplicateRegistrationReturns409() throws Exception {
            String email = uniqueEmail();
            register(email);
            mvc.perform(
                            withJson(
                                    post("/api/auth/register"),
                                    """
                                    {"name":"Otro","email":"%s","password":"ClaveSegura123"}
                                    """
                                            .formatted(email.toUpperCase())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("El email ya está registrado"));
        }

        @Test
        @DisplayName("Datos de registro inválidos responden 400 con el detalle por campo")
        void invalidRegistrationReturns400WithDetails() throws Exception {
            mvc.perform(
                            withJson(
                                    post("/api/auth/register"),
                                    "{\"name\":\"A\",\"email\":\"no-es-email\",\"password\":\"corta\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.email").exists())
                    .andExpect(jsonPath("$.details.password").exists());
        }

        @Test
        @DisplayName("Login correcto devuelve token; contraseña incorrecta responde 401")
        void loginFlow() throws Exception {
            String email = uniqueEmail();
            register(email);

            mvc.perform(
                            withJson(
                                    post("/api/auth/login"),
                                    "{\"email\":\"%s\",\"password\":\"ClaveSegura123\"}"
                                            .formatted(email)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty());

            mvc.perform(
                            withJson(
                                    post("/api/auth/login"),
                                    "{\"email\":\"%s\",\"password\":\"Incorrecta123\"}"
                                            .formatted(email)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Credenciales inválidas."));
        }
    }

    // ------------------------------------------------------------------ archivos

    @Nested
    @DisplayName("Archivos")
    class Archivos {

        @Test
        @DisplayName("Subir y descargar devuelve el contenido original; en disco está cifrado")
        void uploadDownloadRoundTripIsEncryptedAtRest() throws Exception {
            String token = register(uniqueEmail());
            byte[] content =
                    "Contenido confidencial con ñ y acentos: áéíóú"
                            .getBytes(StandardCharsets.UTF_8);

            JsonNode file = upload(token, "confidencial.txt", content);
            assertThat(file.get("checksum").asText()).hasSize(64);

            byte[] downloaded =
                    mvc.perform(auth(get("/api/files/download/" + file.get("id").asText()), token))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsByteArray();
            assertThat(downloaded).isEqualTo(content);

            List<Path> stored;
            try (Stream<Path> files = Files.walk(tempDir.resolve("uploads"))) {
                stored =
                        files.filter(p -> p.getFileName().toString().endsWith("confidencial.txt"))
                                .toList();
            }
            assertThat(stored).hasSize(1);
            byte[] onDisk = Files.readAllBytes(stored.get(0));
            // IV (12 bytes) + texto cifrado + etiqueta GCM (16 bytes)
            assertThat(onDisk).hasSize(content.length + 28);
            assertThat(new String(onDisk, StandardCharsets.ISO_8859_1))
                    .doesNotContain("confidencial");
        }

        @Test
        @DisplayName("Superar la cuota del usuario responde 507")
        void quotaExceededReturns507() throws Exception {
            String token = register(uniqueEmail());
            byte[] big = new byte[(int) USER_QUOTA + 1];

            mvc.perform(
                            auth(
                                    multipart("/api/files/upload")
                                            .file(
                                                    new MockMultipartFile(
                                                            "file",
                                                            "grande.bin",
                                                            "application/octet-stream",
                                                            big)),
                                    token))
                    .andExpect(status().isInsufficientStorage());
        }

        @Test
        @DisplayName("Un UUID inválido en la ruta responde 400, no 500")
        void invalidUuidReturns400() throws Exception {
            String token = register(uniqueEmail());
            mvc.perform(auth(get("/api/files/download/no-es-un-uuid"), token))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Restaurar un archivo que no está en la papelera responde 409")
        void restoreActiveFileReturns409() throws Exception {
            String token = register(uniqueEmail());
            String fileId = upload(token, "a.txt", "a".getBytes()).get("id").asText();

            mvc.perform(auth(post("/api/trash/" + fileId + "/restore"), token))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Listar archivos sin búsqueda y con búsqueda (sin distinguir mayúsculas)")
        void listAndSearchFiles() throws Exception {
            String token = register(uniqueEmail());
            upload(token, "Informe-Anual.pdf", "a".getBytes());
            upload(token, "foto.png", "b".getBytes());

            mvc.perform(auth(get("/api/files"), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2));
            mvc.perform(auth(get("/api/files").param("search", "informe"), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].originalName").value("Informe-Anual.pdf"));
        }
    }

    // ------------------------------------------------------------------ carpetas

    @Nested
    @DisplayName("Carpetas")
    class Carpetas {

        @Test
        @DisplayName("Crear dos carpetas con el mismo nombre en el mismo sitio responde 409")
        void duplicateFolderReturns409() throws Exception {
            String token = register(uniqueEmail());
            createFolder(token, "Fotos");
            mvc.perform(auth(withJson(post("/api/folders"), "{\"name\":\"Fotos\"}"), token))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Borrar una carpeta con archivos (uno ya en la papelera) funciona")
        void deleteFolderWithTrashedFile() throws Exception {
            String token = register(uniqueEmail());
            String folderId = createFolder(token, "Proyecto");

            String trashed =
                    json.readTree(
                                    mvc.perform(
                                                    auth(
                                                            multipart("/api/files/upload")
                                                                    .file(
                                                                            new MockMultipartFile(
                                                                                    "file",
                                                                                    "viejo.txt",
                                                                                    "text/plain",
                                                                                    "v".getBytes()))
                                                                    .param("folderId", folderId),
                                                            token))
                                            .andExpect(status().isCreated())
                                            .andReturn()
                                            .getResponse()
                                            .getContentAsString())
                            .get("id")
                            .asText();
            mvc.perform(
                            auth(
                                    multipart("/api/files/upload")
                                            .file(
                                                    new MockMultipartFile(
                                                            "file",
                                                            "nuevo.txt",
                                                            "text/plain",
                                                            "n".getBytes()))
                                            .param("folderId", folderId),
                                    token))
                    .andExpect(status().isCreated());
            mvc.perform(auth(delete("/api/files/" + trashed), token))
                    .andExpect(status().isNoContent());

            mvc.perform(auth(delete("/api/folders/" + folderId), token))
                    .andExpect(status().isNoContent());

            // Ambos archivos quedan en la papelera
            mvc.perform(auth(get("/api/trash"), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }
    }

    // ------------------------------------------------------------------ compartir

    @Nested
    @DisplayName("Enlaces compartidos")
    class Compartir {

        @Test
        @DisplayName(
                "Ciclo completo: crear, descargar sin sesión, papelera (410) y borrado definitivo")
        void shareLifecycle() throws Exception {
            String token = register(uniqueEmail());
            byte[] content = "compartido".getBytes();
            String fileId = upload(token, "compartido.txt", content).get("id").asText();

            String shareToken =
                    json.readTree(
                                    mvc.perform(
                                                    auth(
                                                            withJson(
                                                                    post(
                                                                            "/api/share/files/"
                                                                                    + fileId),
                                                                    "{\"expirationHours\":2}"),
                                                            token))
                                            .andExpect(status().isCreated())
                                            .andReturn()
                                            .getResponse()
                                            .getContentAsString())
                            .get("token")
                            .asText();

            // Descarga pública, sin token
            byte[] downloaded =
                    mvc.perform(get("/api/share/" + shareToken).with(fromClientIp()))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsByteArray();
            assertThat(downloaded).isEqualTo(content);

            // Archivo en la papelera: el enlace deja de funcionar
            mvc.perform(auth(delete("/api/files/" + fileId), token))
                    .andExpect(status().isNoContent());
            mvc.perform(get("/api/share/" + shareToken).with(fromClientIp()))
                    .andExpect(status().isGone());

            // Borrado definitivo de un archivo con enlaces
            mvc.perform(auth(delete("/api/trash/" + fileId), token))
                    .andExpect(status().isNoContent());
            mvc.perform(get("/api/share/" + shareToken).with(fromClientIp()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Una expiración fuera de rango responde 400")
        void invalidExpirationReturns400() throws Exception {
            String token = register(uniqueEmail());
            String fileId = upload(token, "x.txt", "x".getBytes()).get("id").asText();

            mvc.perform(
                            auth(
                                    withJson(
                                            post("/api/share/files/" + fileId),
                                            "{\"expirationHours\":9999}"),
                                    token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.expirationHours").exists());
        }
    }

    // ------------------------------------------------------------------ sesión web (cookie)

    @Nested
    @DisplayName("Sesión web con cookie")
    class SesionCookie {

        private Cookie loginCookie(String email) throws Exception {
            MvcResult result =
                    mvc.perform(
                                    withJson(
                                            post("/api/auth/login"),
                                            "{\"email\":\"%s\",\"password\":\"ClaveSegura123\"}"
                                                    .formatted(email)))
                            .andExpect(status().isOk())
                            .andReturn();
            return result.getResponse().getCookie(AuthCookies.NAME);
        }

        @Test
        @DisplayName("El login deja una cookie HttpOnly y SameSite=Strict")
        void loginSetsSecureCookie() throws Exception {
            String email = uniqueEmail();
            register(email);
            MvcResult result =
                    mvc.perform(
                                    withJson(
                                            post("/api/auth/login"),
                                            "{\"email\":\"%s\",\"password\":\"ClaveSegura123\"}"
                                                    .formatted(email)))
                            .andReturn();
            String setCookie = result.getResponse().getHeader("Set-Cookie");
            assertThat(setCookie)
                    .startsWith(AuthCookies.NAME + "=")
                    .contains("HttpOnly")
                    .contains("SameSite=Strict")
                    .contains("Path=/");
        }

        @Test
        @DisplayName("Con la cookie se puede consultar /api/auth/me y leer datos")
        void cookieAuthenticatesReads() throws Exception {
            String email = uniqueEmail();
            register(email);
            Cookie cookie = loginCookie(email);

            mvc.perform(get("/api/auth/me").cookie(cookie).with(fromClientIp()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(email))
                    .andExpect(jsonPath("$.token").doesNotExist());
            mvc.perform(get("/api/folders/contents").cookie(cookie).with(fromClientIp()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Con cookie, modificar datos sin la cabecera anti-CSRF responde 401")
        void cookieWithoutCsrfHeaderCannotModify() throws Exception {
            String email = uniqueEmail();
            register(email);
            Cookie cookie = loginCookie(email);

            mvc.perform(withJson(post("/api/folders"), "{\"name\":\"X\"}").cookie(cookie))
                    .andExpect(status().isUnauthorized());
            mvc.perform(
                            withJson(post("/api/folders"), "{\"name\":\"X\"}")
                                    .cookie(cookie)
                                    .header(AuthCookies.CSRF_HEADER, "XMLHttpRequest"))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("El logout borra la cookie")
        void logoutClearsCookie() throws Exception {
            MvcResult result =
                    mvc.perform(post("/api/auth/logout").with(fromClientIp()))
                            .andExpect(status().isNoContent())
                            .andReturn();
            assertThat(result.getResponse().getHeader("Set-Cookie"))
                    .startsWith(AuthCookies.NAME + "=;")
                    .contains("Max-Age=0");
        }

        @Test
        @DisplayName("Las respuestas llevan Content-Security-Policy")
        void responsesIncludeCsp() throws Exception {
            MvcResult result = mvc.perform(get("/api/info").with(fromClientIp())).andReturn();
            assertThat(result.getResponse().getHeader("Content-Security-Policy"))
                    .contains("script-src 'self'")
                    .contains("frame-ancestors 'none'");
        }
    }

    // ------------------------------------------------------------------ papelera y carpetas

    @Nested
    @DisplayName("Papelera y listado de carpetas")
    class PapeleraYCarpetas {

        @Test
        @DisplayName("Vaciar la papelera elimina todos sus archivos y libera la cuota")
        void emptyTrash() throws Exception {
            String token = register(uniqueEmail());
            String a = upload(token, "a.txt", "aaaa".getBytes()).get("id").asText();
            String b = upload(token, "b.txt", "bbbb".getBytes()).get("id").asText();
            upload(token, "c.txt", "cccc".getBytes());
            mvc.perform(auth(delete("/api/files/" + a), token)).andExpect(status().isNoContent());
            mvc.perform(auth(delete("/api/files/" + b), token)).andExpect(status().isNoContent());

            mvc.perform(auth(delete("/api/trash"), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deleted").value(2));
            mvc.perform(auth(get("/api/trash"), token)).andExpect(jsonPath("$.length()").value(0));
            mvc.perform(auth(get("/api/folders/contents"), token))
                    .andExpect(jsonPath("$.storageUsed").value(4));
        }

        @Test
        @DisplayName("GET /api/folders lista todas las carpetas con su carpeta padre")
        void listAllFolders() throws Exception {
            String token = register(uniqueEmail());
            String parent = createFolder(token, "Padre");
            mvc.perform(
                            auth(
                                    withJson(
                                            post("/api/folders"),
                                            "{\"name\":\"Hija\",\"parentId\":\"" + parent + "\"}"),
                                    token))
                    .andExpect(status().isCreated());

            mvc.perform(auth(get("/api/folders"), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].name").value("Hija"))
                    .andExpect(jsonPath("$[0].parentId").value(parent))
                    .andExpect(jsonPath("$[1].name").value("Padre"));
        }
    }

    // ------------------------------------------------------------------ panel B2B

    @Nested
    @DisplayName("Panel de administración B2B")
    class PanelB2b {

        @Test
        @DisplayName("Un usuario que no es administrador recibe 403 aunque sepa la contraseña")
        void nonAdminCannotUseAdminPanel() throws Exception {
            String token = register(uniqueEmail());
            mvc.perform(
                            auth(get("/api/admin/config"), token)
                                    .header("X-B2B-Admin-Password", ADMIN_PASSWORD))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Con contraseña incorrecta responde 403")
        void wrongAdminPasswordReturns403() throws Exception {
            String token = adminToken();
            mvc.perform(
                            auth(get("/api/admin/config"), token)
                                    .header("X-B2B-Admin-Password", "incorrecta"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("La configuración visible nunca incluye secretos")
        void configNeverExposesSecrets() throws Exception {
            String token = adminToken();
            String body =
                    mvc.perform(
                                    auth(get("/api/admin/config"), token)
                                            .header("X-B2B-Admin-Password", ADMIN_PASSWORD))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            JsonNode config = json.readTree(body);
            assertThat(config.fieldNames())
                    .toIterable()
                    .containsExactlyInAnyOrder(
                            "HOST_STORAGE_PATH", "HOST_DB_PATH", "APP_LICENSE_KEY");
        }

        @Test
        @DisplayName("Una licencia con salto de línea (inyección en el .env) se rechaza con 400")
        void licenseInjectionIsRejected() throws Exception {
            String token = adminToken();
            mvc.perform(
                            auth(
                                            withJson(
                                                    post("/api/admin/config"),
                                                    "{\"licenseKey\":\"abc\\nJWT_SECRET=hack\"}"),
                                            token)
                                    .header("X-B2B-Admin-Password", ADMIN_PASSWORD))
                    .andExpect(status().isBadRequest());

            assertThat(Files.readString(tempDir.resolve(".env"))).doesNotContain("JWT_SECRET");
        }
    }
}
