package nerds.studiousTestProject.convenience.repository;

import nerds.studiousTestProject.member.entity.member.Member;
import nerds.studiousTestProject.room.entity.Room;
import nerds.studiousTestProject.room.repository.RoomRepository;
import nerds.studiousTestProject.studycafe.entity.Studycafe;
import nerds.studiousTestProject.studycafe.repository.StudycafeRepository;
import nerds.studiousTestProject.support.RepositoryTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static nerds.studiousTestProject.support.EntitySaveProvider.스터디카페_저장;
import static nerds.studiousTestProject.support.EntitySaveProvider.편의시설_저장;
import static nerds.studiousTestProject.support.EntitySaveProvider.회원_저장;
import static nerds.studiousTestProject.support.fixture.ConvenienceFixture.ROOM_HDMI_FREE;
import static nerds.studiousTestProject.support.fixture.MemberFixture.DEFAULT_USER;
import static nerds.studiousTestProject.support.fixture.RoleFixture.USER;
import static nerds.studiousTestProject.support.fixture.RoomFixture.ROOM_FOUR_SIX;
import static nerds.studiousTestProject.support.fixture.StudycafeFixture.NERDS;

@RepositoryTest
class ConvenienceRepositoryTest {

    @Autowired
    ConvenienceRepository convenienceRepository;
    @Autowired
    RoomRepository roomRepository;
    @Autowired
    StudycafeRepository studycafeRepository;

    @Test
    @DisplayName(value = "룸 id를 통해 (룸)편의시설을 삭제할 수 있다.")
    void deleteAllByRoomId() {
        // given
        Member member = 회원_저장(DEFAULT_USER.생성());
        USER.멤버_생성(member);
        Studycafe studycafe = 스터디카페_저장(NERDS.멤버_생성(member));
        Room room = roomRepository.save(ROOM_FOUR_SIX.스터디카페_생성(studycafe, 1L));
        편의시설_저장(ROOM_HDMI_FREE.룸_생성(room, null));
        // when
        convenienceRepository.deleteAllByRoomId(room.getId());
        // then
        Assertions.assertThat(room.getConveniences()).isEmpty();
    }
}