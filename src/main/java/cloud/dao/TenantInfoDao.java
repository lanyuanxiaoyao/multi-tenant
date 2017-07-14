package cloud.dao;

import cloud.entity.TenantInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantInfoDao extends JpaRepository<TenantInfo, Integer> {
}
