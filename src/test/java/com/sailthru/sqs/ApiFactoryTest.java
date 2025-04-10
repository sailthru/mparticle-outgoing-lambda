package com.sailthru.sqs;

import com.mparticle.ApiClient;
import com.mparticle.client.EventsApi;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.Retrofit;
import software.amazon.awssdk.utils.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ApiFactoryTest {

    @Mock(answer = Answers.RETURNS_MOCKS)
    private ApiClient mockClient;
    @Mock(answer = Answers.RETURNS_MOCKS)
    private Retrofit.Builder mockAdapterBuilder;
    @Mock
    private EventsApi mockEventsApi;
    private List<Pair<String, String>> keysAndSecrets;
    private ApiFactory apiFactory;
    private AtomicInteger currentCallCounter;
    private AtomicInteger maxConcurrentCalls;

    @BeforeEach
    void beforeEach() {
        keysAndSecrets = new ArrayList<>();
        currentCallCounter = new AtomicInteger(0);
        maxConcurrentCalls = new AtomicInteger(0);
        apiFactory = givenApiFactory();
        configureMockClient(mockEventsApi);
        ApiFactory.CACHE.clear();
    }

    private void configureMockClient(EventsApi mockEventsApi1) {
        reset(mockClient);
        when(mockClient.getAdapterBuilder()).thenReturn(mockAdapterBuilder);
        when(mockClient.createService(eq(EventsApi.class))).thenReturn(mockEventsApi1);
    }

    @Test
    void givenSingleKeyAndApiThenClientConfiguredAsExpected() {
        final EventsApi api = apiFactory.of("test", "test", "test");

        verify(mockClient).configureFromOkclient(any());
        verify(mockAdapterBuilder).baseUrl("test");
        verify(mockClient).createService(EventsApi.class);
        assertThat(ApiFactory.CACHE.size(), equalTo(1));
    }

    @Test
    void givenMultipleKeysAndApiThenClientsAreConfiguredAsExpected() {
        final EventsApi api1 = apiFactory.of("test", "test", "test");
        final EventsApi api2 = apiFactory.of("test", "test", "test2");
        final EventsApi api3 = apiFactory.of("test2", "test", "test");
        final EventsApi api4 = apiFactory.of("test", "test2", "test");

        final InOrder inOrder = Mockito.inOrder(mockClient, mockAdapterBuilder);
        final ArgumentCaptor<OkHttpClient> captor = ArgumentCaptor.forClass(OkHttpClient.class);
        inOrder.verify(mockClient).configureFromOkclient(captor.capture());
        inOrder.verify(mockAdapterBuilder).baseUrl("test");
        inOrder.verify(mockClient).createService(EventsApi.class);
        inOrder.verify(mockClient).configureFromOkclient(captor.capture());
        inOrder.verify(mockAdapterBuilder).baseUrl("test2");
        inOrder.verify(mockClient).createService(EventsApi.class);
        inOrder.verify(mockClient).configureFromOkclient(captor.capture());
        inOrder.verify(mockAdapterBuilder).baseUrl("test");
        inOrder.verify(mockClient).createService(EventsApi.class);
        inOrder.verify(mockClient).configureFromOkclient(captor.capture());
        inOrder.verify(mockAdapterBuilder).baseUrl("test");
        inOrder.verify(mockClient).createService(EventsApi.class);
        inOrder.verifyNoMoreInteractions();
        verifyAllCapturedOkHttpClientsAreSameInstance(captor);
        assertThat(keysAndSecrets,
            equalTo(List.of(
                Pair.of("test", "test"),
                Pair.of("test", "test"),
                Pair.of("test2", "test"),
                Pair.of("test", "test2")
            )));
        assertThat(ApiFactory.CACHE.size(), equalTo(4));
    }

    @Test
    void givenMultipleKeysAndApiRequestedInParallelThenOnlyOneClientWasConfiguredAtATime() {
        IntStream.range(0, 10)
            .mapToObj(i -> givenApiFactory())
            .parallel()
            .forEach(apiF ->
                IntStream.range(0, 10)
                    .parallel()
                    .forEach(i -> apiF.of("test", "test", "test" + i))
            );

        assertThat(maxConcurrentCalls.get(), equalTo(1));
        final ArgumentCaptor<OkHttpClient> captor = ArgumentCaptor.forClass(OkHttpClient.class);
        verify(mockClient, times(10)).configureFromOkclient(captor.capture());
        IntStream.range(0, 10)
                .forEach(i -> verify(mockAdapterBuilder).baseUrl("test" + i));
        verify(mockClient, times(10)).createService(EventsApi.class);
        verifyNoMoreInteractions(mockClient, mockAdapterBuilder);
        verifyAllCapturedOkHttpClientsAreSameInstance(captor);
        assertThat(ApiFactory.CACHE.size(), equalTo(10));
    }

    @Test
    void givenMultipleKeysButNeverCachedWithParallelRequestsThenOnlyOneAttemptToConfigureWasMadeAtATime() {
        configureMockClient(null);
        IntStream.range(0, 10)
            .mapToObj(i -> givenApiFactory())
            .parallel()
            .forEach(apiF ->
                IntStream.range(0, 10)
                    .parallel()
                    .forEach(i -> apiF.of("test", "test", "test" + i))
            );

        assertThat(maxConcurrentCalls.get(), equalTo(1));
        final ArgumentCaptor<OkHttpClient> captor = ArgumentCaptor.forClass(OkHttpClient.class);
        verify(mockClient, times(100)).configureFromOkclient(captor.capture());
        IntStream.range(0, 10)
            .forEach(i -> verify(mockAdapterBuilder, times(10)).baseUrl("test" + i));
        verify(mockClient, times(100)).createService(EventsApi.class);
        verifyNoMoreInteractions(mockClient, mockAdapterBuilder);
        verifyAllCapturedOkHttpClientsAreSameInstance(captor);
        assertThat(ApiFactory.CACHE.size(), equalTo(0));
    }

    private static void verifyAllCapturedOkHttpClientsAreSameInstance(ArgumentCaptor<OkHttpClient> captor) {
        assertThat(captor.getAllValues()
            .stream()
            .map(c -> System.identityHashCode(c))
            .distinct()
            .count(), equalTo(1L));
    }

    private ApiFactory givenApiFactory() {
        final ApiFactory apiFactory = new ApiFactory();
        apiFactory.setApiClientFactory((key, secret) -> {
            currentCallCounter.incrementAndGet();
            try {
                // sleep to increase the chance of concurrency
                Thread.sleep(RandomGenerator.getDefault().nextInt(10));
                keysAndSecrets.add(Pair.of(key, secret));
                return mockClient;
            } catch (InterruptedException e) {
                fail("interrupted");
                return null;
            } finally {
                final int currentCallCount = currentCallCounter.getAndDecrement();
                maxConcurrentCalls.getAndAccumulate(currentCallCount, Math::max);
            }
        });
        return apiFactory;
    }
}
