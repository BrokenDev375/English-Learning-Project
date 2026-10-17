package vn.edu.ptit.english.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.edu.ptit.english.dto.ClassroomRecord;
import vn.edu.ptit.english.entity.ClassroomEntity;

public interface ClassroomJpaRepository extends JpaRepository<ClassroomEntity,Long> {
    String VIEW="""
        select new vn.edu.ptit.english.dto.ClassroomRecord(c.id,c.teacher.id,c.teacher.fullName,
        c.name,c.description,c.joinCode,c.createRequestId,
        (select count(cm) from ClassMemberEntity cm where cm.classroom.id=c.id),c.createdAt,m.joinedAt)
        from ClassroomEntity c left join ClassMemberEntity m on m.classroom.id=c.id and m.student.id=:actor
        """;
    @Query(VIEW+" where c.teacher.id=:actor order by c.createdAt desc,c.id desc")
    List<ClassroomRecord> teacherRooms(@Param("actor") long actor);
    @Query(VIEW+" where m.student.id=:actor order by c.createdAt desc,c.id desc")
    List<ClassroomRecord> studentRooms(@Param("actor") long actor);
    @Query(VIEW+" where c.id=:id")
    Optional<ClassroomRecord> viewById(@Param("id") long id,@Param("actor") long actor);
    @Query(VIEW+" where c.joinCode=:code")
    Optional<ClassroomRecord> viewByCode(@Param("code") String code,@Param("actor") long actor);
    @Query(VIEW+" where c.teacher.id=:actor and c.createRequestId=:request")
    Optional<ClassroomRecord> viewByRequest(@Param("actor") long actor,@Param("request") String request);
    boolean existsByJoinCode(String code);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ClassroomEntity c where c.id=:id")
    Optional<ClassroomEntity> lockById(@Param("id") long id);
}
