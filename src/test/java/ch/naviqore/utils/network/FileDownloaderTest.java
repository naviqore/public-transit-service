package ch.naviqore.utils.network;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileDownloaderTest {

    private static final String TEST_FILE_URL = "https://example.com/file.zip";
    private static final String TEST_FILE_NAME = "test.zip";
    @TempDir
    Path tempDirectory;
    @Mock
    private HttpClient httpClientMock;
    @InjectMocks
    private FileDownloader fileDownloader;

    @BeforeEach
    void setUp() {
        fileDownloader = new FileDownloader(httpClientMock, URI.create(TEST_FILE_URL));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldDownloadFileSuccessfully() throws IOException, InterruptedException {
        HttpResponse<?> responseMock = mock(HttpResponse.class);
        when(responseMock.statusCode()).thenReturn(200);
        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(responseMock);

        fileDownloader.downloadTo(tempDirectory, TEST_FILE_NAME, false);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClientMock).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

        HttpRequest capturedRequest = requestCaptor.getValue();
        assertEquals(URI.create(TEST_FILE_URL), capturedRequest.uri());
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldOverwriteExistingFile() throws IOException, InterruptedException {
        HttpResponse<?> responseMock = mock(HttpResponse.class);
        when(responseMock.statusCode()).thenReturn(200);
        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(responseMock);

        Files.createFile(tempDirectory.resolve(TEST_FILE_NAME));

        fileDownloader.downloadTo(tempDirectory, TEST_FILE_NAME, true);

        verify(httpClientMock).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldNotDownloadIfFileExistsAndNoOverwrite() throws IOException, InterruptedException {
        Files.createFile(tempDirectory.resolve(TEST_FILE_NAME));

        fileDownloader.downloadTo(tempDirectory, TEST_FILE_NAME, false);

        verify(httpClientMock, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldHandleDownloadFailure() throws IOException, InterruptedException {
        HttpResponse<?> responseMock = mock(HttpResponse.class);
        when(responseMock.statusCode()).thenReturn(500);
        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(responseMock);

        fileDownloader.downloadTo(tempDirectory, TEST_FILE_NAME, false);

        verify(httpClientMock).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        assertFalse(Files.exists(tempDirectory.resolve(TEST_FILE_NAME)));
    }
}
