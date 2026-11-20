package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.DeviceGame;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceGameRepository extends JpaRepository<DeviceGame, Long> {

    List<DeviceGame> findByBranchIdAndMerchantIdAndDeletedFalseOrderByNameAscIdAsc(Long branchId, Long merchantId);

    Optional<DeviceGame> findByIdAndMerchantIdAndDeletedFalse(Long id, Long merchantId);

    List<DeviceGame> findByIdIn(Collection<Long> ids);

    /** Pairs of (console id, game id) for the given consoles. */
    @Query(value = "select console_id, game_id from tbl_device_console_game where console_id in (:consoleIds)", nativeQuery = true)
    List<Object[]> findLinks(@Param("consoleIds") Collection<Long> consoleIds);

    @Modifying
    @Query(value = "delete from tbl_device_console_game where console_id = :consoleId", nativeQuery = true)
    void unlinkConsole(@Param("consoleId") Long consoleId);

    @Modifying
    @Query(value = "delete from tbl_device_console_game where game_id = :gameId", nativeQuery = true)
    void unlinkGame(@Param("gameId") Long gameId);

    @Modifying
    @Query(value = "insert into tbl_device_console_game (console_id, game_id) values (:consoleId, :gameId) on conflict do nothing",
            nativeQuery = true)
    void link(@Param("consoleId") Long consoleId, @Param("gameId") Long gameId);
}
