package won.matcher.service.common.event;

import won.matcher.service.common.mailbox.PriorityAtomEventMailbox;

/**
 * Indicates how this event was generated and associates that cause with a fixed
 * priority. Used in the {@link PriorityAtomEventMailbox}; lower priority values
 * means more important.
 */
public enum Cause {
    PUSHED(0), // we received a push about this Atom from a WoN node. React fast!
    MATCHED(3), // we found this Atom as a match to another one. React timely.
    SCHEDULED_FOR_REMATCH(5), // we decided it's time to re-match for this Atom. Should be handled before
                              // events generated in crawling
    CRAWLED(10), // we found this Atom during crawling. Handle when we have nothing else to do
    ;
    private int priority;

    private Cause(int prio) {
        this.priority = prio;
    }

    public int getPriority() {
        return priority;
    }

    public static int LOWEST_PRIORTY = Integer.MAX_VALUE;
}