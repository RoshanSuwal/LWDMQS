package org.ekbana.broker.topic;

import lombok.*;
import org.ekbana.broker.segment.SegmentMetaData;

import java.io.Serializable;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class TopicMetaData implements Serializable {
    private SegmentMetaData activeSegmentMetaData;
    private SegmentMetaData passiveSegmentMetaData;
}
