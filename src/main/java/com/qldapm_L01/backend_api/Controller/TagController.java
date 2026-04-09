package com.qldapm_L01.backend_api.Controller;

import com.qldapm_L01.backend_api.Payload.Response.BaseResponse;
import com.qldapm_L01.backend_api.Repository.TagRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tags")
public class TagController {

    @Autowired
    private TagRepository tagRepository;

    @GetMapping
    public ResponseEntity<?> getAllTags() {
        List<TagRepository.TagCountProjection> tags = tagRepository.findTagsWithCount();
        List<Map<String, Object>> tagsResp = tags.stream().map(t -> {
            Map<String, Object> map = new HashMap<>();
            map.put("name", t.getTagName());
            map.put("count", t.getDocCount());
            return map;
        }).collect(Collectors.toList());

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Tags retrieved successfully");
        response.setData(tagsResp);
        return ResponseEntity.ok(response);
    }
}
