package open.dolphin.touch.user;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import open.dolphin.touch.support.TouchErrorMapper;
import open.dolphin.touch.support.TouchRequestContext;
import open.dolphin.touch.support.TouchRequestContextExtractor;

/**
 * Touch ユーザー情報エンドポイント。
 */
@Path("/touch")
@Produces(MediaType.APPLICATION_JSON)
public class TouchUserResource {

    @Inject
    TouchUserService userService;

    @GET
    @Path("/user/{param}")
    @Deprecated
    public TouchUserDtos.TouchUserResponse getUser(@Context HttpServletRequest request,
                                                   @PathParam("param") String param) {
        TouchRequestContext context = TouchRequestContextExtractor.from(request);
        String[] params = param.split(",");
        if (params.length >= 3) {
            throw TouchErrorMapper.toException(Response.Status.GONE,
                    "deprecated_endpoint", "このエンドポイントは廃止されました。新しい summary API を使用してください。", context.traceId());
        }
        if (params.length == 0 || params[0].isBlank()) {
            throw TouchErrorMapper.toException(Response.Status.BAD_REQUEST,
                    "invalid_parameters", "パラメータ形式が不正です。", context.traceId());
        }
        throw TouchErrorMapper.toException(Response.Status.GONE,
                "deprecated_endpoint", "このエンドポイントは廃止されました。新しい summary API を使用してください。", context.traceId());
    }

    @GET
    @Path("/user/summary")
    public TouchUserDtos.TouchUserResponse getUserSummary(@Context HttpServletRequest request) {
        TouchRequestContext context = TouchRequestContextExtractor.from(request);
        String deviceId = request.getHeader("X-Device-Id");
        return userService.getUserSummary(context, deviceId);
    }
}

