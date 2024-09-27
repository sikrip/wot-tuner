package gr.sikrip;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * One entry (line) of logged ECU data.
 */
@Builder
@ToString
@Getter
public  class LogEntry {
    private double timeSeconds;
    private int rpm;
    private double afr;
    private double throttle;
    private int mapN;
    private int mapP;
}
