package com.snowedunderproductions.graphprobe.annotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link DynamicSourceProvider}, focusing on CSV resource loading.
 *
 * @author Paul Snow
 * @since 0.0.0
 */
class DynamicSourceProviderTest {

    @Test
    void readCsvFileThrowsIllegalArgumentExceptionWhenResourceMissing() {
        DynamicSourceProvider provider = new DynamicSourceProvider();

        assertThatThrownBy(() -> provider.readCsvFile("/does-not-exist.csv"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("/does-not-exist.csv");
    }

    @Test
    void readCsvFileReadsAllLinesIncludingHeader() throws Exception {
        DynamicSourceProvider provider = new DynamicSourceProvider();

        List<String> lines = provider.readCsvFile("/test-data/sample.csv");

        assertThat(lines)
            .containsExactly("id,name", "1,alice", "2,bob");
    }
}
