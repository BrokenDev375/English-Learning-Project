package vn.edu.ptit.english;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.socket.*;
import org.springframework.util.LinkedMultiValueMap;
import vn.edu.ptit.english.dto.*;
import vn.edu.ptit.english.dto.AdminSessionViews.User;
import vn.edu.ptit.english.enums.*;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.repository.AdminSessionRepository;
import vn.edu.ptit.english.security.*;
import vn.edu.ptit.english.service.impl.AdminSessionServiceImpl;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminSessionServiceTest {
    final LiveConnectionsTest.Time time=new LiveConnectionsTest.Time();
    final SessionActivityRegistry registry=new SessionActivityRegistry(time,300,30);
    final LiveConnections live=new LiveConnections(time,new ObjectMapper(),75000);
    final AdminSessionRepository repository=mock(AdminSessionRepository.class);
    final AccountGuard guard=mock(AccountGuard.class);
    final AdminSessionServiceImpl service=new AdminSessionServiceImpl(registry,live,repository,guard,time);
    final UserPrincipal admin=new UserPrincipal(99,"admin@test.local","",Role.ADMIN,AccountStatus.ACTIVE,0);
    final AdminSessionQuery all=new AdminSessionQuery("",null,null,0,20);
    AdminSessionServiceTest(){
        live.sessionActivity(registry);
        when(repository.users(anySet())).thenAnswer(call->((Set<Long>)call.getArgument(0)).stream()
            .map(id->new User(id,"User "+id,"u"+id+"@test.local",Role.STUDENT,AccountStatus.ACTIVE)).toList());
        when(repository.attempts(anySet())).thenReturn(List.of());
    }
    MockHttpSession session(long user){var s=new MockHttpSession(null,"raw-"+UUID.randomUUID());live.register(s,user);return s;}
    String tab(MockHttpSession s,String activity,String visibility,Long attempt){
        String id=UUID.randomUUID().toString();registry.report(s.getId(),id,0,new SessionActivityRegistry.Context(activity,attempt,visibility),false);return id;
    }
    WebSocketSession socket(MockHttpSession s,long user,String tab){
        var w=mock(WebSocketSession.class);when(w.getId()).thenReturn(UUID.randomUUID().toString());when(w.isOpen()).thenReturn(true);
        live.attach(w,new UserPrincipal(user,"u@test.local","",Role.STUDENT,AccountStatus.ACTIVE,0),s.getId());live.bindTab(w.getId(),tab);return w;
    }
    @Test void deduplicatesAccountsCountsSeparateSessionsAndSelectsVisibleInteractingTab(){
        var a=session(1);var b=session(1);var c=session(2);
        String visible=tab(a,"LEARN","VISIBLE",null),hidden=tab(a,"HOME","HIDDEN",null),other=tab(b,"PROFILE","VISIBLE",null);
        socket(a,1,visible);socket(a,1,hidden);socket(b,1,other);socket(c,2,tab(c,"HOME","VISIBLE",null));
        time.advance(1000);registry.report(a.getId(),visible,1,new SessionActivityRegistry.Context("LEARN",null,"VISIBLE"),true);
        time.advance(1000);registry.report(a.getId(),hidden,1,new SessionActivityRegistry.Context("HOME",null,"HIDDEN"),true);
        var page=service.list(all,admin);
        assertThat(page.summary().connectedAccounts()).isEqualTo(2);assertThat(page.summary().validSessions()).isEqualTo(3);
        assertThat(page.summary().openConnections()).isEqualTo(4);assertThat(page.items().get(0).primaryContext().activity()).isEqualTo("LEARN");
        var limited=service.list(new AdminSessionQuery("u1@",Role.STUDENT,"LEARN",1,1),admin);
        assertThat(limited.items()).isEmpty();assertThat(limited.totalElements()).isEqualTo(1);assertThat(limited.summary().connectedAccounts()).isEqualTo(1);
        verify(repository,times(2)).users(anySet());verify(repository,times(2)).attempts(anySet());
    }
    @Test void lostSocketStillHasValidDetailAndReadsNeverRenewDeadline(){
        var s=session(1);var w=socket(s,1,tab(s,"HOME","VISIBLE",null));
        var ref=service.list(all,admin).items().get(0).sessions().get(0).sessionRef();
        var before=registry.state(s.getId());live.detach(w.getId());time.advance(60000);
        var detail=service.detail(ref,admin);
        assertThat(detail.session().connected()).isFalse();assertThat(detail.session().interactionState()).isEqualTo("IDLE");
        assertThat(detail.session().idleExpiresAt()).isEqualTo(before.idleExpiresAt());assertThat(service.list(all,admin).items()).isEmpty();
        time.advance(240000);assertThatThrownBy(()->service.detail(ref,admin)).isInstanceOf(ApiException.class);
        assertThat(s.isInvalid()).isFalse(); // GET filters; expiry worker owns invalidation.
    }
    @Test void metadataIsOwnerCheckedAndModeResolvedFromServer(){
        var s=session(1);socket(s,1,tab(s,"RESULT","VISIBLE",42L));
        when(repository.attempts(Set.of(42L))).thenReturn(List.of(new AdminSessionRepository.AttemptContext(42,2,"REVIEW","SUBMITTED","Foreign secret",2)));
        assertThat(service.list(all,admin).items().get(0).primaryContext().activity()).isEqualTo("OTHER");
        when(repository.attempts(Set.of(42L))).thenReturn(List.of(new AdminSessionRepository.AttemptContext(42,1,"REVIEW","IN_PROGRESS",null,null)));
        var c=service.list(all,admin).items().get(0).primaryContext();assertThat(c.activity()).isEqualTo("REVIEW");assertThat(c.attemptId()).isEqualTo(42);
    }
    @Test void opaqueRefsAndExpiredTabsDoNotExposeInternalIds() throws Exception {
        var s=session(1);var t=tab(s,"HOME","VISIBLE",null);var w=socket(s,1,t);
        var page=service.list(all,admin);String ref=page.items().get(0).sessions().get(0).sessionRef();
        String json=new ObjectMapper().findAndRegisterModules().writeValueAsString(service.detail(ref,admin));
        assertThat(json).doesNotContain(s.getId(),t,"JSESSIONID","securityVersion","passwordHash","selection","answers");
        assertThatThrownBy(()->service.detail(s.getId(),admin)).isInstanceOf(ApiException.class);
        time.advance(75000);live.heartbeat(w.getId());assertThat(service.detail(ref,admin).tabs()).isEmpty();
        assertThat(service.list(all,admin).items().get(0).visibility()).isEqualTo("UNKNOWN");
    }
    @Test void wrongRoleAndRevokedActorCannotRead(){
        var student=new UserPrincipal(1,"s@test.local","",Role.STUDENT,AccountStatus.ACTIVE,0);
        assertThatThrownBy(()->service.list(all,student)).isInstanceOf(ApiException.class);
        doThrow(AccountGuard.revoked()).when(guard).requireCurrent(admin);
        assertThatThrownBy(()->service.detail(UUID.randomUUID().toString(),admin)).isInstanceOf(ApiException.class);
        verifyNoInteractions(repository);
    }
    @Test void heartbeatKeepsBoundTabMetadataAliveWithoutRenewingInteractionDeadline(){
        var s=session(1);var w=socket(s,1,tab(s,"HOME","HIDDEN",null));
        var before=registry.state(s.getId());
        for(int i=0;i<3;i++){time.advance(30000);live.heartbeat(w.getId());}
        var source=registry.snapshot().get(0);
        var detail=service.detail(source.sessionRef(),admin);
        assertThat(detail.tabs()).hasSize(1);assertThat(detail.tabs().get(0).connected()).isTrue();
        assertThat(detail.tabs().get(0).lastInteractionAt()).isNull();
        assertThat(detail.session().idleExpiresAt()).isEqualTo(before.idleExpiresAt());assertThat(detail.session().interactionState()).isEqualTo("IDLE");
    }
    @Test void strictQueryRejectsUnknownDuplicateInvalidAndOversizedInputs(){
        for(var pair:List.of(new String[]{"size","0"},new String[]{"size","51"},new String[]{"page","-1"},new String[]{"role","ROOT"},new String[]{"activity","IDLE"},new String[]{"userId","1"},new String[]{"q","x".repeat(101)})){
            var p=new LinkedMultiValueMap<String,String>();p.add(pair[0],pair[1]);assertThatThrownBy(()->AdminSessionQuery.parse(p)).isInstanceOf(ApiException.class);
        }
        var p=new LinkedMultiValueMap<String,String>();p.add("q","a");p.add("q","b");assertThatThrownBy(()->AdminSessionQuery.parse(p)).isInstanceOf(ApiException.class);
    }
    @Test void contextInvalidationIsCoalescedAdminOnlyAndNoPeriodicIdleBroadcast()throws Exception{
        var a=session(99);var w=mock(WebSocketSession.class);when(w.getId()).thenReturn("admin");when(w.isOpen()).thenReturn(true);
        live.attach(w,admin,a.getId());live.subscribePresence("admin",true);
        var s=session(1);var t=tab(s,"HOME","VISIBLE",null);var student=socket(s,1,t);
        time.advance(1000);live.flushPresence();clearInvocations(w,student);
        registry.report(s.getId(),t,1,new SessionActivityRegistry.Context("PROFILE",null,"VISIBLE"),false);
        registry.report(s.getId(),t,2,new SessionActivityRegistry.Context("CATALOG",null,"HIDDEN"),false);
        verify(w,never()).sendMessage(any());time.advance(1000);live.flushPresence();
        verify(w,times(1)).sendMessage(argThat(m->m.getPayload().equals("{\"version\":1,\"type\":\"PRESENCE_CHANGED\"}")));
        verify(student,never()).sendMessage(any());clearInvocations(w);
        time.advance(60000);live.flushPresence();verify(w,never()).sendMessage(any());
    }
}
