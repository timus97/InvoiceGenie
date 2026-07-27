package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.adapter.persistence.entity.PostingPeriodEntity;
import com.invoicegenie.ar.domain.model.period.PostingPeriod;
import com.invoicegenie.ar.domain.model.period.PostingPeriodRepository;
import com.invoicegenie.ar.domain.model.period.PostingPeriodStatus;
import com.invoicegenie.shared.domain.TenantId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PostingPeriodRepositoryAdapter implements PostingPeriodRepository {

    @PersistenceContext
    EntityManager em;

    @Override
    @Transactional
    public void save(TenantId tenantId, PostingPeriod period) {
        em.merge(toEntity(period));
    }

    @Override
    public Optional<PostingPeriod> findById(TenantId tenantId, UUID id) {
        PostingPeriodEntity e = em.find(PostingPeriodEntity.class, id);
        if (e == null || !e.getTenantId().equals(tenantId.getValue())) {
            return Optional.empty();
        }
        return Optional.of(toDomain(e));
    }

    @Override
    public List<PostingPeriod> findAllByTenant(TenantId tenantId) {
        return em.createQuery(
                        "SELECT e FROM PostingPeriodEntity e WHERE e.tenantId = :tid ORDER BY e.periodStart DESC",
                        PostingPeriodEntity.class)
                .setParameter("tid", tenantId.getValue())
                .getResultStream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public boolean hasAnyPeriods(TenantId tenantId) {
        Long count = em.createQuery(
                        "SELECT COUNT(e) FROM PostingPeriodEntity e WHERE e.tenantId = :tid",
                        Long.class)
                .setParameter("tid", tenantId.getValue())
                .getSingleResult();
        return count != null && count > 0;
    }

    @Override
    public Optional<PostingPeriod> findOpenCovering(TenantId tenantId, LocalDate day) {
        List<PostingPeriodEntity> rows = em.createQuery(
                        "SELECT e FROM PostingPeriodEntity e WHERE e.tenantId = :tid AND e.status = 'OPEN' "
                                + "AND e.periodStart <= :day AND e.periodEnd >= :day "
                                + "ORDER BY e.periodStart DESC",
                        PostingPeriodEntity.class)
                .setParameter("tid", tenantId.getValue())
                .setParameter("day", day)
                .setMaxResults(1)
                .getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.of(toDomain(rows.get(0)));
    }

    private PostingPeriodEntity toEntity(PostingPeriod p) {
        PostingPeriodEntity e = new PostingPeriodEntity();
        e.setId(p.getId());
        e.setTenantId(p.getTenantId().getValue());
        e.setPeriodStart(p.getPeriodStart());
        e.setPeriodEnd(p.getPeriodEnd());
        e.setStatus(p.getStatus().name());
        e.setClosedAt(p.getClosedAt());
        e.setClosedBy(p.getClosedBy());
        e.setNotes(p.getNotes());
        e.setCreatedAt(p.getCreatedAt());
        e.setUpdatedAt(p.getUpdatedAt());
        return e;
    }

    private PostingPeriod toDomain(PostingPeriodEntity e) {
        return new PostingPeriod(
                e.getId(),
                TenantId.of(e.getTenantId()),
                e.getPeriodStart(),
                e.getPeriodEnd(),
                PostingPeriodStatus.valueOf(e.getStatus()),
                e.getClosedAt(),
                e.getClosedBy(),
                e.getNotes(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
