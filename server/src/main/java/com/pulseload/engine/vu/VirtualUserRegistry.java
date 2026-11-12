package com.pulseload.engine.vu;

import com.pulseload.engine.executor.ExecutionOutcome;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, bounded store of per-virtual-user state for in-process runs. The
 * closed-model
 * strategy reports lifecycle transitions here from each VU's own thread; the
 * control plane reads
 * snapshots back through the {@code LoadGenerator} port to drive the
 * observability UI.
 *
 * <p>
 * Deliberately bounded: it retains state only for the most recent
 * {@value #MAX_RETAINED_RUNS}
 * runs so a long-lived server doesn't accumulate VU maps indefinitely, and it
 * stores only
 * non-sensitive lifecycle facts (never request headers, bodies, or
 * credentials).
 */
@Component
public class VirtualUserRegistry {

  private static final int MAX_RETAINED_RUNS = 16;

  // Access-ordered so the eldest *least-recently-touched* run is evicted once the
  // cap is hit.
  private final Map<UUID, Map<Integer, MutableState>> byRun = Collections.synchronizedMap(
      new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Map<Integer, MutableState>> eldest) {
          return size() > MAX_RETAINED_RUNS;
        }
      });

  public void started(UUID runId, int vuIndex) {
    states(runId).put(vuIndex, new MutableState(vuIndex));
  }

  public void requestStarted(UUID runId, int vuIndex) {
    MutableState state = states(runId).get(vuIndex);
    if (state != null) {
      state.activity = VirtualUserActivity.REQUESTING;
      state.lastActivityAt = Instant.now();
    }
  }

  public void requestCompleted(UUID runId, int vuIndex, ExecutionOutcome outcome) {
    MutableState state = states(runId).get(vuIndex);
    if (state != null) {
      state.activity = VirtualUserActivity.IDLE;
      state.requestCount++;
      state.lastOutcome = outcome;
      state.lastActivityAt = Instant.now();
    }
  }

  public void finished(UUID runId, int vuIndex, boolean failed) {
    MutableState state = states(runId).get(vuIndex);
    if (state != null) {
      state.activity = failed ? VirtualUserActivity.ERROR : VirtualUserActivity.COMPLETED;
      state.lastActivityAt = Instant.now();
    }
  }

  public List<VirtualUserState> snapshot(UUID runId) {
    Map<Integer, MutableState> states = byRun.get(runId);
    if (states == null) {
      return List.of();
    }
    synchronized (states) {
      return states.values().stream()
          .map(MutableState::toImmutable)
          .sorted(Comparator.comparingInt(VirtualUserState::id))
          .toList();
    }
  }

  private Map<Integer, MutableState> states(UUID runId) {
    return byRun.computeIfAbsent(runId, id -> new ConcurrentHashMap<>());
  }

  private static final class MutableState {
    private final int id;
    private volatile VirtualUserActivity activity = VirtualUserActivity.IDLE;
    private volatile long requestCount;
    private volatile ExecutionOutcome lastOutcome;
    private volatile Instant lastActivityAt = Instant.now();

    private MutableState(int id) {
      this.id = id;
    }

    private VirtualUserState toImmutable() {
      return new VirtualUserState(id, activity, requestCount, lastOutcome, lastActivityAt);
    }
  }
}
