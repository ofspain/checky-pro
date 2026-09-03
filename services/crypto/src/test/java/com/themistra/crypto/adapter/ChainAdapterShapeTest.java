package com.themistra.crypto.adapter;

import com.themistra.crypto.adapter.model.FinalityStatus;
import com.themistra.crypto.adapter.model.Subscription;
import com.themistra.crypto.adapter.model.TokenInfo;
import com.themistra.crypto.adapter.model.TxResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC1 — {@code ChainAdapter} matches design.md §4c's verbatim interface exactly. Scoped per frozen
 * brief amendment #9 to method name / return type / parameter *types* only — not parameter names
 * (not preserved by reflection without {@code -parameters}, which this project does not compile
 * with) and not declaration order or annotations.
 */
class ChainAdapterShapeTest {

    private record MethodShape(String name, Class<?> returnType, List<Class<?>> parameterTypes) {
    }

    @Test
    void chainAdapterIsAnInterface() {
        // Phase 11 Gap 12: name/return-type/parameter-type comparison alone wouldn't fail if this
        // were refactored into an abstract class - design.md §4c fixes it as "public interface".
        assertThat(ChainAdapter.class.isInterface()).isTrue();
    }

    @Test
    void chainAdapterHasExactlyTheFiveMethodsDesignSpecifiesVerbatim() {
        List<MethodShape> expected = List.of(
                new MethodShape("chain", Chain.class, List.of()),
                new MethodShape("getTx", TxResult.class, List.of(String.class)),
                new MethodShape("getTokenInfo", TokenInfo.class, List.of(String.class)),
                new MethodShape("subscribeAddress", Subscription.class, List.of(String.class, ObservationSink.class)),
                new MethodShape("getFinalityStatus", FinalityStatus.class, List.of(String.class))
        );

        List<MethodShape> actual = Arrays.stream(ChainAdapter.class.getDeclaredMethods())
                .map(ChainAdapterShapeTest::toShape)
                .sorted(Comparator.comparing(MethodShape::name))
                .collect(Collectors.toList());

        List<MethodShape> expectedSorted = expected.stream()
                .sorted(Comparator.comparing(MethodShape::name))
                .collect(Collectors.toList());

        assertThat(actual).isEqualTo(expectedSorted);
    }

    private static MethodShape toShape(Method method) {
        return new MethodShape(method.getName(), method.getReturnType(), List.of(method.getParameterTypes()));
    }
}
