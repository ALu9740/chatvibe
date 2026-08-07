package com.chatvibe.module.admin.service;

import com.chatvibe.common.result.PageResult;
import com.chatvibe.module.admin.dto.CreateAnnouncementDTO;
import com.chatvibe.module.admin.vo.AnnouncementVO;

/**
 * 管理员公告服务接口
 *
 * @author Alu
 * @date 2026-08-07
 */
public interface AdminAnnouncementService {

    /**
     * 分页查询公告列表（支持标题搜索）
     *
     * @param keyword 搜索关键词(可选，模糊匹配标题)
     * @param page    页码
     * @param size    每页大小
     * @return 公告分页结果
     */
    PageResult<AnnouncementVO> getAnnouncementList(String keyword, int page, int size);

    /**
     * 创建公告
     *
     * @param dto 公告信息
     * @return 是否创建成功
     */
    boolean createAnnouncement(CreateAnnouncementDTO dto);

    /**
     * 撤回公告
     *
     * @param id 公告ID
     * @return 是否撤回成功
     */
    boolean withdrawAnnouncement(Long id);
}
