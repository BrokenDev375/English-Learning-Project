package vn.edu.ptit.english;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import vn.edu.ptit.english.security.SessionActivityRegistry;
import vn.edu.ptit.english.exception.ApiException;
import static org.assertj.core.api.Assertions.*;

class SessionActivityRegistryTest {
    final LiveConnectionsTest.Time time=new LiveConnectionsTest.Time();
    final SessionActivityRegistry registry=new SessionActivityRegistry(time,300,30);
    final SessionActivityRegistry.Context context=new SessionActivityRegistry.Context("HOME",null,"VISIBLE");
    MockHttpSession login(long user) {var s=new MockHttpSession();registry.register(s,user);return s;}
    @Test void exactBoundaryAndWarningUseServerClockWithoutRequestsExtendingIt() {
        var s=login(1);var initial=registry.state(s.getId());
        assertThat(initial.warningAt()).isEqualTo(time.instant().plusSeconds(270));
        time.advance(299999);assertThat(registry.state(s.getId()).idleExpiresAt()).isEqualTo(initial.idleExpiresAt());
        time.advance(1);assertThatThrownBy(()->registry.state(s.getId())).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("SESSION_IDLE_TIMEOUT"));
        assertThat(s.isInvalid()).isTrue();assertThat(registry.idleReason(s.getId())).isTrue();
    }
    @Test void contextRegistrationAndDuplicateActivityNeverRenewTheSession() {
        var s=login(1);String id=s.getId(),tab=UUID.randomUUID().toString();
        var start=registry.report(id,tab,0,context,false);
        time.advance(10000);assertThat(registry.report(id,tab,1,context,false).idleExpiresAt()).isEqualTo(start.idleExpiresAt());
        time.advance(1000);var active=registry.report(id,tab,2,context,true);
        time.advance(10000);assertThat(registry.report(id,tab,2,context,true).idleExpiresAt()).isEqualTo(active.idleExpiresAt());
        assertThat(registry.report(id,tab,1,context,true).idleExpiresAt()).isEqualTo(active.idleExpiresAt());
    }
    @Test void twoTabsShareOneDeadlineButAnotherLoginDoesNot() {
        var a=login(1);var b=login(1);String tab=UUID.randomUUID().toString();
        registry.report(a.getId(),tab,0,context,false);time.advance(10000);
        var newer=registry.report(a.getId(),tab,1,context,true);
        assertThat(registry.state(a.getId()).idleExpiresAt()).isEqualTo(newer.idleExpiresAt());
        assertThat(registry.state(b.getId()).idleExpiresAt()).isBefore(newer.idleExpiresAt());
    }
    @Test void expiredSessionCannotBeResurrectedAndOldTabCannotRegisterAsActivity() {
        var s=login(1);String id=s.getId(),tab=UUID.randomUUID().toString();
        assertThatThrownBy(()->registry.report(id,tab,0,context,true)).isInstanceOf(ApiException.class);
        registry.report(id,tab,0,context,false);time.advance(300000);
        assertThatThrownBy(()->registry.report(id,tab,1,context,true)).isInstanceOf(ApiException.class);
        assertThat(registry.current(id)).isFalse();
    }
    @Test void staleTabWatermarkIsNotLostWhenDisplayMetadataExpires() {
        var s=login(1);String id=s.getId(),tab=UUID.randomUUID().toString();
        registry.report(id,tab,0,context,false);registry.report(id,tab,1,context,true);time.advance(75000);
        assertThatThrownBy(()->registry.report(id,tab,2,context,true)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("TAB_EXPIRED"));
        registry.report(id,tab,0,context,false);
        assertThatThrownBy(()->registry.report(id,tab,1,context,true)).isInstanceOf(ApiException.class);
    }
    @Test void sweepClosesOnlyIdleSessionAndCleansReasonAfterTtl() {
        var a=login(1);var b=login(2);String tab=UUID.randomUUID().toString();registry.report(b.getId(),tab,0,context,false);
        time.advance(60000);registry.report(b.getId(),tab,1,context,true);
        var closed=new ArrayList<String>();registry.onClosing(closed::add);time.advance(240000);registry.sweep();
        assertThat(closed).containsExactly(a.getId());assertThat(registry.current(b.getId())).isTrue();
        time.advance(300001);registry.sweep();assertThat(registry.idleReason(a.getId())).isFalse();
    }
    @Test void floodAndTabCountAreBounded() {
        var s=login(1);
        for(int i=0;i<16;i++)registry.report(s.getId(),UUID.randomUUID().toString(),0,context,false);
        assertThatThrownBy(()->registry.report(s.getId(),UUID.randomUUID().toString(),0,context,false)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("TAB_LIMIT"));
    }
    @Test void expiryCompetingWithTouchNeverRevivesTheSession() throws Exception {
        var s=login(1);String id=s.getId(),tab=UUID.randomUUID().toString();registry.report(id,tab,0,context,false);time.advance(300000);
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {
            var a=pool.submit(()->{try{gate.await();registry.report(id,tab,1,context,true);}catch(Exception ignored){}});
            var b=pool.submit(()->{try{gate.await();registry.sweep();}catch(Exception ignored){}});
            gate.countDown();a.get(5,TimeUnit.SECONDS);b.get(5,TimeUnit.SECONDS);
            assertThat(registry.current(id)).isFalse();assertThat(s.isInvalid()).isTrue();
        } finally {pool.shutdownNow();}
    }
    @Test void invalidConfigurationFailsAtStartup() {
        assertThatThrownBy(()->new SessionActivityRegistry(time,0,30)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new SessionActivityRegistry(time,30,30)).isInstanceOf(IllegalArgumentException.class);
    }
}
