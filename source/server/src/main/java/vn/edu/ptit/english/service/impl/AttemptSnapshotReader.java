package vn.edu.ptit.english.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.stereotype.Component;
import vn.edu.ptit.english.dto.AttemptData.Answer;
import vn.edu.ptit.english.dto.AttemptViews.*;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.enums.AttemptEnums.PracticeRole;
import vn.edu.ptit.english.repository.*;
import vn.edu.ptit.english.service.PublishedContentReader;

/** Read-only projection of pinned versions. Called within the owning service transaction. */
@Component
public class AttemptSnapshotReader {
    private final AttemptRepository attempts;
    private final PublishedContentReader content;
    private final ContentRepository versions;
    private final CatalogRepository catalog;
    private final AssetRepository assets;
    public AttemptSnapshotReader(AttemptRepository attempts,PublishedContentReader content,ContentRepository versions,
        CatalogRepository catalog,AssetRepository assets) {
        this.attempts=attempts; this.content=content; this.versions=versions; this.catalog=catalog; this.assets=assets;
    }
    public Snapshot read(AttemptEntity attempt) {
        var stored=attempts.items(attempt.getId());
        var answers=new HashMap<Long,Answer>();
        attempts.answers(stored.stream().map(AttemptQuestionEntity::getId).toList()).forEach(a->answers.put(a.getAttemptQuestionId(),a.getResponse()));
        var materials=new LinkedHashMap<Long,PublicMaterial>(); var items=new ArrayList<Item>();
        for(var item:stored) {
            var q=content.questionVersion(item.getQuestionVersionId()).data();
            ObjectNode display=(ObjectNode)q.content().deepCopy();
            if(q.type().equals("SINGLE_CHOICE")) reorder(display,"options",item.getDisplay().optionIds());
            if(q.type().equals("ORDERING")) reorder(display,"tokens",item.getDisplay().optionIds());
            if(q.type().equals("MATCHING")) reorder(display,"rightItems",item.getDisplay().rightItemIds());
            if(q.materialVersionId()!=null) materials.computeIfAbsent(q.materialVersionId(),this::material);
            boolean guided=item.getPracticeRole()==PracticeRole.GUIDED;
            items.add(new Item(item.getId(),item.getQuestionVersionId(),item.getOriginKey(),q.objectiveId(),q.difficulty(),
                item.getPracticeRole(),item.getPosition(),item.isWasSeenBefore(),guided&&q.hint()!=null&&!q.hint().isBlank(),
                item.getHintOpenedAt()!=null,item.getFeedbackOpenedAt()!=null,new PublicQuestion(q.type(),q.prompt(),display),
                q.materialVersionId(),answers.get(item.getId())));
        }
        Long submission=attempts.submission(attempt.getId()).map(SubmissionEntity::getId).orElse(null);
        return new Snapshot(attempt.getId(),attempt.getMode(),attempt.getState(),attempt.getLessonVersionId(),attempt.getRevision(),
            attempt.getCreatedAt(),attempt.getUpdatedAt(),attempt.getSubmittedAt(),List.copyOf(items),List.copyOf(materials.values()),
            attempt.getPlayerState(),submission,attempt.getLessonVersionId()==null?null:lesson(attempt.getLessonVersionId()));
    }
    private void reorder(ObjectNode content,String field,List<String> order) {
        if(order.isEmpty()) return;
        var originals=new HashMap<String,JsonNode>(); content.path(field).forEach(v->originals.put(v.path("id").asText(),v));
        if(order.size()!=originals.size()||!new HashSet<>(order).equals(originals.keySet())) throw new IllegalStateException("Invalid stored question permutation");
        var target=content.putArray(field); order.forEach(id->target.add(originals.get(id)));
    }
    private PublicMaterial material(long version) {
        var d=content.materialVersion(version).data(); PublicAsset asset=null;
        if(d.assetId()!=null) {
            var a=assets.find(d.assetId()).orElseThrow(AuthoringAccess::missing).view();
            asset=new PublicAsset(a.id(),a.mimeType(),a.sizeBytes(),a.url());
        }
        return new PublicMaterial(version,d.kind(),d.title(),d.readingText(),asset,d.altText(),d.caption(),
            d.startSeconds()==null?null:d.startSeconds().doubleValue(),d.endSeconds()==null?null:d.endSeconds().doubleValue(),d.source());
    }
    private LessonPublic lesson(long versionId) {
        var pinned=content.lessonVersion(versionId);
        var version=versions.lessonVersion(versionId).orElseThrow(AuthoringAccess::missing);
        var root=versions.lesson(pinned.lessonId(),false).orElseThrow(AuthoringAccess::missing); var d=version.data();
        int guided=0,independent=0,review=0;
        for(var slot:pinned.questions()) switch(slot.practiceRole()) {
            case "GUIDED" -> guided++; case "INDEPENDENT" -> independent++; case "REVIEW_VARIANT" -> review++;
            default -> throw new IllegalStateException("Invalid stored practice role");
        }
        var summary=new LessonSummary(pinned.lessonId(),pinned.unitId(),versionId,pinned.title(),d.description(),root.getSortOrder(),
            pinned.objectiveIds(),new QuestionCounts(guided,independent,review));
        return new LessonPublic(summary,pinned.instructions(),pinned.objectiveIds().stream()
            .map(id->catalog.objective(id,false).orElseThrow(AuthoringAccess::missing).view()).toList());
    }
}
