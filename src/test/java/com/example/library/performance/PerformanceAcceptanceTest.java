package com.example.library.performance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reproducible local HTTP acceptance measurement against the dedicated MySQL test database. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PerformanceAcceptanceTest {

    private static final int BOOK_COUNT = 100;
    private static final int COPY_COUNT = 300;
    private static final int READER_COUNT = 50;
    private static final int HISTORY_COUNT = 200;
    private static final int CONCURRENT_SESSIONS = 20;
    private static final int WARMUP_ROUNDS = 5;
    private static final int MEASURED_ROUNDS = 15;
    private static final long P95_LIMIT_NANOS = Duration.ofSeconds(2).toNanos();
    private static final String PASSWORD = "t21-performance-pass-789";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern CSRF_INPUT = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final String runId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private String userPrefix;
    private String readerNoPrefix;
    private String categoryName;
    private String bookPrefix;
    private String barcodePrefix;
    private long categoryId;
    private long operatorId;
    private List<Long> bookIds;
    private List<Long> copyIds;

    @BeforeEach
    void seedSyntheticDataset() {
        userPrefix = "t21p" + runId + "x";
        readerNoPrefix = "T21P" + runId + "-R-";
        categoryName = "T21P" + runId + "-CATEGORY";
        bookPrefix = "T21P" + runId + "-BOOK-";
        barcodePrefix = "T21P" + runId + "-COPY-";
        cleanupSyntheticDataset();

        categoryId = insertAndGetId("INSERT INTO category (name) VALUES (?)",
                statement -> statement.setString(1, categoryName));

        String encodedPassword = passwordEncoder.encode(PASSWORD);
        List<Object[]> users = new ArrayList<>(READER_COUNT + 1);
        users.add(new Object[]{userPrefix + "staff", encodedPassword, "T21 性能验收馆员", "LIBRARIAN"});
        for (int index = 0; index < READER_COUNT; index++) {
            users.add(new Object[]{readerLogin(index), encodedPassword,
                    "T21 性能验收读者 " + index, "READER"});
        }
        jdbc.batchUpdate("INSERT INTO app_user " +
                "(login_name, password_hash, display_name, role, status, must_change_password, credential_version) " +
                "VALUES (?, ?, ?, ?, 'ACTIVE', FALSE, 0)", users);
        operatorId = jdbc.queryForObject("SELECT id FROM app_user WHERE login_name = ?", Long.class,
                userPrefix + "staff");

        List<Long> readerUserIds = jdbc.query(
                "SELECT id FROM app_user WHERE login_name LIKE ? ORDER BY login_name",
                (result, row) -> result.getLong(1), userPrefix + "reader%");
        assertEquals(READER_COUNT, readerUserIds.size());
        List<Object[]> profiles = new ArrayList<>(READER_COUNT);
        for (int index = 0; index < readerUserIds.size(); index++) {
            profiles.add(new Object[]{readerUserIds.get(index), readerNo(index)});
        }
        jdbc.batchUpdate("INSERT INTO reader_profile (user_id, reader_no) VALUES (?, ?)", profiles);

        List<Object[]> books = new ArrayList<>(BOOK_COUNT);
        for (int index = 0; index < BOOK_COUNT; index++) {
            books.add(new Object[]{categoryId, bookTitle(index), "T21 性能测试作者", "本地图书馆",
                    "合成数据用于目录和详情并发验收。".repeat(8)});
        }
        jdbc.batchUpdate("INSERT INTO book " +
                "(category_id, title, author, publisher, description, status) VALUES (?, ?, ?, ?, ?, 'ACTIVE')", books);
        bookIds = jdbc.query("SELECT id FROM book WHERE title LIKE ? ORDER BY title",
                (result, row) -> result.getLong(1), bookPrefix + "%");
        assertEquals(BOOK_COUNT, bookIds.size());

        List<Object[]> copies = new ArrayList<>(COPY_COUNT);
        for (int index = 0; index < COPY_COUNT; index++) {
            copies.add(new Object[]{bookIds.get(index / 3), barcode(index), "T21-SHELF-" + (index % 12)});
        }
        jdbc.batchUpdate("INSERT INTO book_copy (book_id, barcode, shelf_location, status) " +
                "VALUES (?, ?, ?, 'AVAILABLE')", copies);
        copyIds = jdbc.query("SELECT id FROM book_copy WHERE barcode LIKE ? ORDER BY barcode",
                (result, row) -> result.getLong(1), barcodePrefix + "%");
        assertEquals(COPY_COUNT, copyIds.size());

        List<Long> readerProfileIds = jdbc.query(
                "SELECT id FROM reader_profile WHERE reader_no LIKE ? ORDER BY reader_no",
                (result, row) -> result.getLong(1), readerNoPrefix + "%");
        assertEquals(READER_COUNT, readerProfileIds.size());
        Instant oldestBorrowedAt = Instant.now().minus(Duration.ofDays(120));
        List<Object[]> history = new ArrayList<>(HISTORY_COUNT);
        for (int index = 0; index < HISTORY_COUNT; index++) {
            Instant borrowedAt = oldestBorrowedAt.plus(Duration.ofMinutes(index));
            history.add(new Object[]{readerProfileIds.get(index % READER_COUNT), copyIds.get(index),
                    java.sql.Timestamp.from(borrowedAt),
                    LocalDate.ofInstant(borrowedAt, BUSINESS_ZONE).plusDays(14),
                    java.sql.Timestamp.from(borrowedAt.plus(Duration.ofDays(16))), operatorId, operatorId});
        }
        jdbc.batchUpdate("INSERT INTO loan " +
                "(reader_id, copy_id, borrowed_at, due_date, returned_at, checkout_operator_id, return_operator_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)", history);

        assertEquals(BOOK_COUNT, count("SELECT COUNT(*) FROM book WHERE title LIKE ?", bookPrefix + "%"));
        assertEquals(COPY_COUNT, count("SELECT COUNT(*) FROM book_copy WHERE barcode LIKE ?", barcodePrefix + "%"));
        assertEquals(READER_COUNT, count("SELECT COUNT(*) FROM app_user WHERE login_name LIKE ?", userPrefix + "reader%"));
        assertEquals(HISTORY_COUNT, count("SELECT COUNT(*) FROM loan l JOIN book_copy c ON c.id = l.copy_id " +
                "WHERE c.barcode LIKE ? AND l.returned_at IS NOT NULL", barcodePrefix + "%"));
    }

    @AfterEach
    void removeSyntheticDataset() {
        cleanupSyntheticDataset();
    }

    @Test
    void twentyAuthenticatedSessionsMeetCatalogListAndDetailP95Target() throws Exception {
        List<SessionClient> sessions = new ArrayList<>(CONCURRENT_SESSIONS);
        for (int index = 0; index < CONCURRENT_SESSIONS; index++) {
            sessions.add(login(readerLogin(index)));
        }
        Set<String> sessionIds = new HashSet<>();
        for (SessionClient session : sessions) {
            session.cookieManager().getCookieStore().getCookies().stream()
                    .filter(cookie -> cookie.getName().equalsIgnoreCase("JSESSIONID"))
                    .map(HttpCookie::getValue).forEach(sessionIds::add);
        }
        assertEquals(CONCURRENT_SESSIONS, sessionIds.size(), "每个并发客户端必须持有独立的已认证会话");

        List<Measurement> measurements = runConcurrentRounds(sessions);
        List<Measurement> catalogSamples = measurements.stream()
                .filter(sample -> sample.endpoint().equals("catalog")).toList();
        List<Measurement> detailSamples = measurements.stream()
                .filter(sample -> sample.endpoint().equals("detail")).toList();
        long catalogP95 = percentile95(catalogSamples);
        long detailP95 = percentile95(detailSamples);
        long overallP95 = percentile95(measurements);
        long serverErrors = measurements.stream().filter(sample -> sample.status() >= 500).count();
        long unexpectedResponses = measurements.stream().filter(sample -> sample.status() != 200).count();

        System.out.printf(Locale.ROOT,
                "T21_PERF os=%s java=%s processors=%d maxHeapMiB=%d books=%d copies=%d readers=%d history=%d " +
                        "sessions=%d warmupRounds=%d measuredRounds=%d requests=%d catalogP95Ms=%.2f " +
                        "detailP95Ms=%.2f overallP95Ms=%.2f unexpected5xx=%d unexpectedResponses=%d%n",
                System.getProperty("os.name"), System.getProperty("java.version"),
                Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory() / (1024 * 1024),
                BOOK_COUNT, COPY_COUNT, READER_COUNT, HISTORY_COUNT, CONCURRENT_SESSIONS,
                WARMUP_ROUNDS, MEASURED_ROUNDS, measurements.size(), toMillis(catalogP95), toMillis(detailP95),
                toMillis(overallP95), serverErrors, unexpectedResponses);

        assertEquals(CONCURRENT_SESSIONS * MEASURED_ROUNDS * 2, measurements.size());
        assertEquals(0, serverErrors, "并发目录请求不得产生意外 5xx");
        assertEquals(0, unexpectedResponses, "目录和详情请求都应返回 HTTP 200");
        assertTrue(catalogP95 <= P95_LIMIT_NANOS,
                "目录列表 P95 超过 2 秒：" + String.format(Locale.ROOT, "%.2f ms", toMillis(catalogP95)));
        assertTrue(detailP95 <= P95_LIMIT_NANOS,
                "书目详情 P95 超过 2 秒：" + String.format(Locale.ROOT, "%.2f ms", toMillis(detailP95)));
        assertTrue(overallP95 <= P95_LIMIT_NANOS,
                "列表与详情合并 P95 超过 2 秒：" + String.format(Locale.ROOT, "%.2f ms", toMillis(overallP95)));
    }

    private List<Measurement> runConcurrentRounds(List<SessionClient> sessions) throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(CONCURRENT_SESSIONS);
        CyclicBarrier startTogether = new CyclicBarrier(CONCURRENT_SESSIONS);
        try {
            List<Future<List<Measurement>>> results = new ArrayList<>(CONCURRENT_SESSIONS);
            for (int workerIndex = 0; workerIndex < CONCURRENT_SESSIONS; workerIndex++) {
                int index = workerIndex;
                SessionClient session = sessions.get(workerIndex);
                results.add(workers.submit(() -> {
                    List<Measurement> ownSamples = new ArrayList<>(MEASURED_ROUNDS * 2);
                    for (int round = 0; round < WARMUP_ROUNDS + MEASURED_ROUNDS; round++) {
                        awaitRound(startTogether);
                        Measurement catalog = request(session.client(), "/catalog?q=" +
                                encode("T21P" + runId) + "&page=0&size=20&sort=titleAsc", "catalog");
                        if (round >= WARMUP_ROUNDS) ownSamples.add(catalog);

                        awaitRound(startTogether);
                        long bookId = bookIds.get((index + round) % bookIds.size());
                        Measurement detail = request(session.client(), "/catalog/" + bookId, "detail");
                        if (round >= WARMUP_ROUNDS) ownSamples.add(detail);
                    }
                    return ownSamples;
                }));
            }
            List<Measurement> allSamples = new ArrayList<>(CONCURRENT_SESSIONS * MEASURED_ROUNDS * 2);
            for (Future<List<Measurement>> result : results) {
                allSamples.addAll(result.get(4, TimeUnit.MINUTES));
            }
            return allSamples;
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(20, TimeUnit.SECONDS), "并发测量工作线程应全部结束");
        }
    }

    private static void awaitRound(CyclicBarrier barrier)
            throws InterruptedException, BrokenBarrierException, TimeoutException {
        barrier.await(45, TimeUnit.SECONDS);
    }

    private Measurement request(HttpClient client, String path, String endpoint) {
        long startedAt = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                    .timeout(Duration.ofSeconds(30)).GET().build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Measurement(System.nanoTime() - startedAt, response.statusCode(), endpoint);
        } catch (IOException exception) {
            return new Measurement(System.nanoTime() - startedAt, 599, endpoint);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new Measurement(System.nanoTime() - startedAt, 599, endpoint);
        }
    }

    private SessionClient login(String loginName) throws IOException, InterruptedException {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<String> page = client.send(HttpRequest.newBuilder(URI.create(baseUrl() + "/login"))
                        .timeout(Duration.ofSeconds(20)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, page.statusCode(), "登录表单必须能访问");
        Matcher csrf = CSRF_INPUT.matcher(page.body());
        assertTrue(csrf.find(), "登录表单必须包含 CSRF 令牌");
        String body = formValue("username", loginName) + "&" + formValue("password", PASSWORD) + "&" +
                formValue("_csrf", csrf.group(1));
        HttpRequest login = HttpRequest.newBuilder(URI.create(baseUrl() + "/login"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        HttpResponse<String> response = client.send(login,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(302, response.statusCode(), "合成读者应成功登录并建立独立 HTTP 会话");
        return new SessionClient(client, cookies);
    }

    private long insertAndGetId(String sql, SqlBinder binder) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            binder.bind(statement);
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new IllegalStateException("MySQL 未返回新记录编号");
        return key.longValue();
    }

    private int count(String sql, Object parameter) {
        return jdbc.queryForObject(sql, Integer.class, parameter);
    }

    private void cleanupSyntheticDataset() {
        if (barcodePrefix != null) {
            jdbc.update("DELETE FROM loan WHERE copy_id IN " +
                    "(SELECT id FROM book_copy WHERE barcode LIKE ?)", barcodePrefix + "%");
            jdbc.update("DELETE FROM book_copy WHERE barcode LIKE ?", barcodePrefix + "%");
        }
        if (bookPrefix != null) jdbc.update("DELETE FROM book WHERE title LIKE ?", bookPrefix + "%");
        if (userPrefix != null) {
            jdbc.update("DELETE FROM reader_profile WHERE user_id IN " +
                    "(SELECT id FROM app_user WHERE login_name LIKE ?)", userPrefix + "%");
            jdbc.update("DELETE FROM app_user WHERE login_name LIKE ?", userPrefix + "%");
        }
        if (categoryName != null) jdbc.update("DELETE FROM category WHERE name = ?", categoryName);
        if (barcodePrefix != null) {
            assertEquals(0, count("SELECT COUNT(*) FROM loan l JOIN book_copy c ON c.id = l.copy_id " +
                    "WHERE c.barcode LIKE ?", barcodePrefix + "%"));
            assertEquals(0, count("SELECT COUNT(*) FROM book_copy WHERE barcode LIKE ?", barcodePrefix + "%"));
        }
        if (bookPrefix != null) assertEquals(0, count("SELECT COUNT(*) FROM book WHERE title LIKE ?", bookPrefix + "%"));
        if (userPrefix != null) {
            assertEquals(0, count("SELECT COUNT(*) FROM reader_profile rp JOIN app_user u ON u.id = rp.user_id " +
                    "WHERE u.login_name LIKE ?", userPrefix + "%"));
            assertEquals(0, count("SELECT COUNT(*) FROM app_user WHERE login_name LIKE ?", userPrefix + "%"));
        }
        if (categoryName != null) {
            assertEquals(0, count("SELECT COUNT(*) FROM category WHERE name = ?", categoryName));
        }
    }

    private String readerLogin(int index) {
        return userPrefix + "reader" + String.format(Locale.ROOT, "%02d", index);
    }

    private String readerNo(int index) {
        return readerNoPrefix + String.format(Locale.ROOT, "%02d", index);
    }

    private String bookTitle(int index) {
        return bookPrefix + String.format(Locale.ROOT, "%03d", index);
    }

    private String barcode(int index) {
        return barcodePrefix + String.format(Locale.ROOT, "%03d", index);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    private static String formValue(String name, String value) {
        return encode(name) + "=" + encode(value);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static long percentile95(List<Measurement> samples) {
        List<Long> durations = samples.stream().map(Measurement::elapsedNanos).sorted(Comparator.naturalOrder()).toList();
        int index = (int) Math.ceil(0.95 * durations.size()) - 1;
        return durations.get(Math.max(0, index));
    }

    private static double toMillis(long nanoseconds) {
        return nanoseconds / 1_000_000.0;
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    private record SessionClient(HttpClient client, CookieManager cookieManager) {
    }

    private record Measurement(long elapsedNanos, int status, String endpoint) {
    }
}
