package ch.naviqore.gtfs.schedule.model;

import ch.naviqore.gtfs.schedule.type.TransferType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
public class Transfer {
    private final Stop fromStop;
    private final Stop toStop;
    private final TransferType transferType;
    @Nullable
    private final Integer minTransferTime;

    public Optional<Integer> getMinTransferTime() {
        return Optional.ofNullable(minTransferTime);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Transfer that = (Transfer) obj;
        return Objects.equals(fromStop, that.fromStop) && Objects.equals(toStop, that.toStop) && Objects.equals(
                transferType, that.transferType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromStop, toStop, transferType);
    }

    @Override
    public String toString() {
        return "Transfer[" + "fromStopId='" + fromStop.getId() + '\'' + ", toStopId='" + toStop.getId() + '\'' + ", transferType=" + transferType + ']';
    }
}