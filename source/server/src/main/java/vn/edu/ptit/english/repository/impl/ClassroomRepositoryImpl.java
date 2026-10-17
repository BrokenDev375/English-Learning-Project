package vn.edu.ptit.english.repository.impl;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.english.dto.ClassroomRecord;
import vn.edu.ptit.english.dto.MemberView;
import vn.edu.ptit.english.entity.ClassMemberEntity;
import vn.edu.ptit.english.entity.ClassMemberKey;
import vn.edu.ptit.english.entity.ClassroomEntity;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.repository.ClassMemberJpaRepository;
import vn.edu.ptit.english.repository.ClassroomJpaRepository;
import vn.edu.ptit.english.repository.ClassroomRepository;
import vn.edu.ptit.english.repository.UserJpaRepository;

@Repository
@Transactional(readOnly=true)
public class ClassroomRepositoryImpl implements ClassroomRepository {
    private final ClassroomJpaRepository rooms;
    private final ClassMemberJpaRepository members;
    private final UserJpaRepository users;
    public ClassroomRepositoryImpl(ClassroomJpaRepository rooms,ClassMemberJpaRepository members,UserJpaRepository users){
        this.rooms=rooms;this.members=members;this.users=users;
    }
    @Override
    public List<ClassroomRecord> list(long actor,Role role){return role==Role.TEACHER?rooms.teacherRooms(actor):rooms.studentRooms(actor);}
    @Override
    public Optional<ClassroomRecord> findById(long id,long actor){return rooms.viewById(id,actor);}
    @Override
    public Optional<ClassroomRecord> findByCode(String code,long actor){return rooms.viewByCode(code,actor);}
    @Override
    public Optional<ClassroomRecord> findByRequest(long teacher,String request){return rooms.viewByRequest(teacher,request);}
    @Override
    public boolean codeExists(String code){return rooms.existsByJoinCode(code);}
    @Override
    @Transactional public void lockTeacher(long id){users.lockById(id).orElseThrow();}
    @Override
    @Transactional public Optional<ClassroomEntity> lock(long id){return rooms.lockById(id);}
    @Override
    @Transactional public long create(long teacher,String name,String description,String code,String request) {
        return rooms.saveAndFlush(new ClassroomEntity(users.getReferenceById(teacher),name,description,code,request)).getId();
    }
    @Override
    @Transactional public void edit(long id,String name,String description){rooms.findById(id).orElseThrow().edit(name,description);}
    @Override
    public List<MemberView> members(long id){return members.members(id);}
    @Override
    public boolean isMember(long classroom,long student){return members.existsById(new ClassMemberKey(classroom,student));}
    @Override
    @Transactional public void addMember(long classroom,long student){
        members.saveAndFlush(new ClassMemberEntity(rooms.getReferenceById(classroom),users.getReferenceById(student)));
    }
    @Override
    @Transactional public int removeMember(long classroom,long student) {
        var key=new ClassMemberKey(classroom,student);
        if(!members.existsById(key))return 0;
        members.deleteById(key);members.flush();return 1;
    }
}
