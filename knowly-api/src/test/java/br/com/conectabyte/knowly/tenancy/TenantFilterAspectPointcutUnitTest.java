package br.com.conectabyte.knowly.tenancy;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.lang.reflect.Field;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Isolated unit-level pointcut test: proves {@link TenantFilterAspect}'s {@code @Around} advice
 * fires for a plain {@code @Transactional} service method but must NOT fire for a
 * {@code @Transactional}-annotated method declared on a type implementing Spring Data's {@link
 * Repository} marker interface -- exactly the shape of {@code SimpleJpaRepository}'s own internal
 * transactional methods (e.g. {@code findById}/{@code save}), which is what caused
 * TenantFilterAspect to force-enable the fail-closed sentinel tenant filter for background
 * {@code @RabbitListener} consumers with no active tenant in context (see {@code
 * ArticleExtractionListener}), silently hiding rows the caller had every right to see by explicit
 * id.
 *
 * <p>This test builds a real AspectJ-woven proxy directly (bypassing Spring Data's own proxy
 * pipeline entirely) so the pointcut expression itself is exercised deterministically, independent
 * of how Spring Data happens to construct its repository proxies in any given Spring version.
 */
class TenantFilterAspectPointcutUnitTest {

    interface PlainService {
        Object doWork();
    }

    static class PlainServiceImpl implements PlainService {
        @Override
        @Transactional
        public Object doWork() {
            return "done";
        }
    }

    interface DummyRepository extends Repository<Object, Long> {
        Object doWork();
    }

    static class DummyRepositoryImpl implements DummyRepository {
        @Override
        @Transactional
        public Object doWork() {
            return "done";
        }
    }

    @Test
    void adviceFiresForAPlainTransactionalServiceMethod() throws Exception {
        EntityManager entityManager = mockEntityManager();
        TenantFilterAspect aspect = newAspect(entityManager);

        AspectJProxyFactory factory = new AspectJProxyFactory(new PlainServiceImpl());
        factory.addAspect(aspect);
        PlainService proxy = factory.getProxy();

        proxy.doWork();

        verify(entityManager, times(1)).unwrap(Session.class);
    }

    @Test
    void adviceDoesNotFireForATransactionalMethodOnARepositoryType() throws Exception {
        EntityManager entityManager = mockEntityManager();
        TenantFilterAspect aspect = newAspect(entityManager);

        AspectJProxyFactory factory = new AspectJProxyFactory(new DummyRepositoryImpl());
        factory.addAspect(aspect);
        DummyRepository proxy = factory.getProxy();

        proxy.doWork();

        verify(entityManager, never()).unwrap(Session.class);
    }

    private EntityManager mockEntityManager() {
        EntityManager entityManager = mock(EntityManager.class);
        Session session = mock(Session.class, RETURNS_DEEP_STUBS);
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        return entityManager;
    }

    /**
     * Builds a real {@link TenantFilterAspect} with a mocked {@link EntityManager} wired in via
     * reflection, since {@code @PersistenceContext} injection doesn't apply outside a Spring
     * container in this pure AspectJ-proxy unit test.
     */
    private TenantFilterAspect newAspect(EntityManager entityManager) throws Exception {
        TenantFilterAspect aspect = new TenantFilterAspect(new TenantContext());

        Field field = TenantFilterAspect.class.getDeclaredField("entityManager");
        field.setAccessible(true);
        field.set(aspect, entityManager);

        return aspect;
    }
}
