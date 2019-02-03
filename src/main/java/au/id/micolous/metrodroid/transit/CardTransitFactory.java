package au.id.micolous.metrodroid.transit;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public interface CardTransitFactory<T> {
    @NotNull
    default List<CardInfo> getAllCards() {
        return Collections.emptyList();
    }

    @Nullable
    TransitIdentity parseTransitIdentity(@NotNull T card);

    @Nullable
    TransitData parseTransitData(@NotNull T card);

    boolean check(@NotNull T card);
}
