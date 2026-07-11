package com.chatvibe.module.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建群组 DTO
 *
 * @author Alu
 * @date 2026-06-28
 */
@Data
public class CreateGroupDTO {

    @NotBlank(message = "群名称不能为空")
    @Size(max = 20, message = "群名称长度不能超过20")
    private String name;

    @NotEmpty(message = "成员列表不能为空")
    private List<Long> memberIds;

    /**
     * 群头像
     */
    private String avatar;
}
